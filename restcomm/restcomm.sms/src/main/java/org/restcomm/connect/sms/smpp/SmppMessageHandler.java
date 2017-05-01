package org.restcomm.connect.sms.smpp;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.actor.UntypedActorContext;
import akka.actor.UntypedActorFactory;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.cloudhopper.commons.charset.CharsetUtil;
import com.cloudhopper.smpp.pdu.SubmitSm;
import com.cloudhopper.smpp.type.Address;
import com.cloudhopper.smpp.type.RecoverablePduException;
import com.cloudhopper.smpp.type.SmppChannelException;
import com.cloudhopper.smpp.type.SmppInvalidArgumentException;
import com.cloudhopper.smpp.type.SmppTimeoutException;
import com.cloudhopper.smpp.type.UnrecoverablePduException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import org.apache.commons.configuration.Configuration;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.commons.util.UriUtils;
import org.restcomm.connect.dao.AccountsDao;
import org.restcomm.connect.dao.ApplicationsDao;
import org.restcomm.connect.dao.DaoManager;
import org.restcomm.connect.dao.IncomingPhoneNumbersDao;
import org.restcomm.connect.dao.entities.Application;
import org.restcomm.connect.dao.entities.IncomingPhoneNumber;
import org.restcomm.connect.dao.entities.Organization;
import org.restcomm.connect.interpreter.StartInterpreter;
import org.restcomm.connect.monitoringservice.MonitoringService;
import org.restcomm.connect.sms.SmsSession;
import org.restcomm.connect.sms.api.CreateSmsSession;
import org.restcomm.connect.sms.api.DestroySmsSession;
import org.restcomm.connect.sms.api.SmsServiceResponse;

import javax.servlet.ServletContext;
import javax.servlet.sip.SipFactory;
import javax.servlet.sip.SipServlet;
import javax.servlet.sip.SipURI;
import java.io.IOException;
import java.net.URI;
import java.util.List;

public class SmppMessageHandler extends UntypedActor  {

    private final LoggingAdapter logger = Logging.getLogger(getContext().system(), this);
    private final ActorSystem system = getContext().system();
    private final ServletContext servletContext;
    private final DaoManager storage;
    private final Configuration configuration;
    private final SipFactory sipFactory;
    private final ActorRef monitoringService;

    private final String defaultOrganization;

    public SmppMessageHandler(final ServletContext servletContext) {
        this.servletContext = servletContext;
        this.storage = (DaoManager) servletContext.getAttribute(DaoManager.class.getName());
        this.configuration = (Configuration) servletContext.getAttribute(Configuration.class.getName());
        this.sipFactory = (SipFactory) servletContext.getAttribute(SipFactory.class.getName());
        this.monitoringService = (ActorRef) servletContext.getAttribute(MonitoringService.class.getName());
        defaultOrganization = (String) servletContext.getAttribute("defaultOrganization");
    }

    @Override
    public void onReceive(Object message) throws Exception {
        final UntypedActorContext context = getContext();
        final ActorRef sender = sender();
        final ActorRef self = self();
        if (message instanceof SmppInboundMessageEntity){
            if(logger.isInfoEnabled()) {
                logger.info("SmppMessageHandler processing Inbound Message " + message.toString());
            }
            inbound((SmppInboundMessageEntity) message);
        }else if(message instanceof SmppOutboundMessageEntity ){
            if(logger.isInfoEnabled()) {
                logger.info("SmppMessageHandler processing Outbound Message " + message.toString());
            }
            outbound((SmppOutboundMessageEntity) message);
        } else if (message instanceof CreateSmsSession) {
            CreateSmsSession createSmsSession = (CreateSmsSession) message;
            final ActorRef session = session(getOrganizationSidByAccountSid(new Sid(createSmsSession.getAccountSid())));
            final SmsServiceResponse<ActorRef> response = new  SmsServiceResponse<ActorRef>(session);
            sender.tell(response, self);
        }else if (message instanceof DestroySmsSession) {
            final DestroySmsSession destroySmsSession = (DestroySmsSession) message;
            final ActorRef session = destroySmsSession.session();
            context.stop(session);
        }
    }

    private void inbound(final SmppInboundMessageEntity request ) throws IOException {
        final ActorRef self = self();

        String to = request.getSmppTo();
        final IncomingPhoneNumbersDao numbersDao = storage.getIncomingPhoneNumbersDao();
        List<IncomingPhoneNumber> numbers = numbersDao.getIncomingPhoneNumber(to);
        IncomingPhoneNumber number = numbers.get(0);

        if( redirectToHostedSmsApp(self,request, storage.getAccountsDao(), storage.getApplicationsDao(),to  )){
            if(logger.isInfoEnabled()) {
                logger.info("SMPP Message Accepted - A Restcomm Hosted App is Found for Number : " + number.getPhoneNumber() );
            }
            return;
        } else {
            logger.error("SMPP Message Rejected : No Restcomm Hosted App Found for inbound number : " + to );
        }
    }

    private boolean redirectToHostedSmsApp(final ActorRef self, final SmppInboundMessageEntity request, final AccountsDao accounts,
                                           final ApplicationsDao applications, String id) throws IOException {
        boolean isFoundHostedApp = false;

        String to = request.getSmppTo();
        String phone = to;

        final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();

        try {
            phone = phoneNumberUtil.format(phoneNumberUtil.parse(to, "US"), PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (Exception e) {}
        // Try to find an application defined for the phone number.
        final IncomingPhoneNumbersDao numbersDao = storage.getIncomingPhoneNumbersDao();
        List<IncomingPhoneNumber> numbers = numbersDao.getIncomingPhoneNumber(phone);
        IncomingPhoneNumber number = null;
        if(!numbers.isEmpty()){
            number = numbers.get(0);
        }

        if(number == null){
            numbers = numbersDao.getIncomingPhoneNumber(to);
            number = numbers.isEmpty() ? null : numbers.get(0);
        }

        if(number == null){
            // https://github.com/Mobicents/RestComm/issues/84 using wildcard as default application
            numbers = numbersDao.getIncomingPhoneNumber("*");
            number = numbers.isEmpty() ? null : numbers.get(0);
        }
        try {
            if (number != null) {
                ActorRef interpreter = null;

                URI appUri = number.getSmsUrl();

                final SmppInterpreterBuilder builder = new SmppInterpreterBuilder(system);
                builder.setSmsService(self);
                builder.setConfiguration(configuration);
                builder.setStorage(storage);
                builder.setAccount(number.getAccountSid());
                builder.setVersion(number.getApiVersion());
                final Sid sid = number.getSmsApplicationSid();
                if (sid != null) {
                    final Application application = applications.getApplication(sid);
                    builder.setUrl(UriUtils.resolve(application.getRcmlUrl()));
                } else if (appUri != null) {
                    builder.setUrl(UriUtils.resolve(appUri));
                } else {
                    logger.error("the matched number doesn't have SMS application attached, number: "+number.getPhoneNumber());
                    return false;
                }
                builder.setMethod(number.getSmsMethod());
                URI appFallbackUrl = number.getSmsFallbackUrl();
                if (appFallbackUrl != null) {
                    builder.setFallbackUrl(UriUtils.resolve(number.getSmsFallbackUrl()));
                    builder.setFallbackMethod(number.getSmsFallbackMethod());
                }
                interpreter = builder.build();

                Sid organizationSid = storage.getOrganizationsDao().getOrganization(storage.getAccountsDao().getAccount(number.getAccountSid()).getOrganizationSid()).getSid();
                if(logger.isDebugEnabled())
                    logger.debug("redirectToHostedSmsApp organizationSid = "+organizationSid);
                final ActorRef session = session(organizationSid);
                session.tell(request, self);
                final StartInterpreter start = new StartInterpreter(session);
                interpreter.tell(start, self);
                isFoundHostedApp = true;

            }
        } catch (Exception e) {
            logger.error("Error processing inbound SMPP Message. There is no locally hosted Restcomm app for the number :" + e);
        }
        return isFoundHostedApp;
    }


    @SuppressWarnings("unchecked")
    private SipURI outboundInterface() {
        SipURI result = null;
        final List<SipURI> uris = (List<SipURI>) servletContext.getAttribute(SipServlet.OUTBOUND_INTERFACES);
        for (final SipURI uri : uris) {
            final String transport = uri.getTransportParam();
            if ("udp".equalsIgnoreCase(transport)) {
                result = uri;
            }
        }
        return result;
    }

    private ActorRef session(final Sid organizationSid) {
        final Props props = new Props(new UntypedActorFactory() {
            private static final long serialVersionUID = 1L;

            @Override
            public UntypedActor create() throws Exception {
                return new SmsSession(configuration, sipFactory, outboundInterface(), storage, monitoringService, servletContext, organizationSid);
            }
        });
        return system.actorOf(props);
    }

    public void outbound(SmppOutboundMessageEntity request) throws SmppInvalidArgumentException, IOException {
//        if(logger.isInfoEnabled()) {
//            logger.info("Message is Received by the SmppSessionOutbound Class");
//        }

        byte[] textBytes;
        int smppTonNpiValue =  Integer.parseInt(SmppService.getSmppTonNpiValue()) ;
        // add delivery receipt
        //submit0.setRegisteredDelivery(SmppConstants.REGISTERED_DELIVERY_SMSC_RECEIPT_REQUESTED);
        SubmitSm submit0 = new SubmitSm();
        submit0.setSourceAddress(new Address((byte)smppTonNpiValue, (byte) smppTonNpiValue, request.getSmppFrom() ));
        submit0.setDestAddress(new Address((byte)smppTonNpiValue, (byte)smppTonNpiValue, request.getSmppTo()));
        if (CharsetUtil.CHARSET_UCS_2 == request.getSmppEncoding()) {
            submit0.setDataCoding(DataCoding.DATA_CODING_UCS2);
            textBytes = CharsetUtil.encode(request.getSmppContent(), CharsetUtil.CHARSET_UCS_2);
        } else {
            submit0.setDataCoding(DataCoding.DATA_CODING_GSM7);
            textBytes = CharsetUtil.encode(request.getSmppContent(), request.getSmppEncoding());
        }

        submit0.setShortMessage(textBytes);
        try {
            if(logger.isInfoEnabled()) {
                logger.info("Sending SubmitSM for " + request);
            }
            SmppClientOpsThread.getSmppSession().submit(submit0, 10000); //send message through SMPP connector
        } catch (RecoverablePduException | UnrecoverablePduException
                | SmppTimeoutException | SmppChannelException
                | InterruptedException e) {
            logger.error("SMPP message cannot be sent : " + e );
        }
    }

    /**
     * getOrganizationSidByAccountSid
     * @param accountSid
     * @return Sid of Organization
     */
    private Sid getOrganizationSidByAccountSid(final Sid accountSid){
        if(accountSid != null){
            return storage.getAccountsDao().getAccount(accountSid).getOrganizationSid();
        }
        Organization organization = storage.getOrganizationsDao().getOrganization(new Sid(defaultOrganization));
        if(logger.isDebugEnabled())
            logger.debug("organization is null going to choose default: "+organization);
        return organization.getSid();
    }
}
