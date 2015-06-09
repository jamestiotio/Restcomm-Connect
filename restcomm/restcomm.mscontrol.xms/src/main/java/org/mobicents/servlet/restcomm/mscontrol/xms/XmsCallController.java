/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2013, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.mobicents.servlet.restcomm.mscontrol.xms;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.media.mscontrol.EventType;
import javax.media.mscontrol.MediaEvent;
import javax.media.mscontrol.MediaEventListener;
import javax.media.mscontrol.MediaSession;
import javax.media.mscontrol.MsControlException;
import javax.media.mscontrol.MsControlFactory;
import javax.media.mscontrol.Parameter;
import javax.media.mscontrol.Parameters;
import javax.media.mscontrol.join.Joinable.Direction;
import javax.media.mscontrol.mediagroup.MediaGroup;
import javax.media.mscontrol.mediagroup.Player;
import javax.media.mscontrol.mediagroup.PlayerEvent;
import javax.media.mscontrol.mediagroup.Recorder;
import javax.media.mscontrol.mediagroup.RecorderEvent;
import javax.media.mscontrol.mediagroup.SpeechDetectorConstants;
import javax.media.mscontrol.mediagroup.signals.SignalDetector;
import javax.media.mscontrol.mediagroup.signals.SignalDetectorEvent;
import javax.media.mscontrol.mixer.MediaMixer;
import javax.media.mscontrol.networkconnection.NetworkConnection;
import javax.media.mscontrol.networkconnection.SdpPortManagerEvent;
import javax.media.mscontrol.resource.RTC;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.apache.commons.configuration.Configuration;
import org.joda.time.DateTime;
import org.mobicents.servlet.restcomm.dao.DaoManager;
import org.mobicents.servlet.restcomm.dao.RecordingsDao;
import org.mobicents.servlet.restcomm.entities.Recording;
import org.mobicents.servlet.restcomm.entities.Sid;
import org.mobicents.servlet.restcomm.fsm.FiniteStateMachine;
import org.mobicents.servlet.restcomm.fsm.State;
import org.mobicents.servlet.restcomm.fsm.Transition;
import org.mobicents.servlet.restcomm.mscontrol.MediaServerController;
import org.mobicents.servlet.restcomm.mscontrol.MediaServerInfo;
import org.mobicents.servlet.restcomm.mscontrol.exceptions.MediaServerControllerException;
import org.mobicents.servlet.restcomm.mscontrol.messages.CloseMediaSession;
import org.mobicents.servlet.restcomm.mscontrol.messages.Collect;
import org.mobicents.servlet.restcomm.mscontrol.messages.CreateMediaGroup;
import org.mobicents.servlet.restcomm.mscontrol.messages.CreateMediaSession;
import org.mobicents.servlet.restcomm.mscontrol.messages.DestroyMediaGroup;
import org.mobicents.servlet.restcomm.mscontrol.messages.JoinBridge;
import org.mobicents.servlet.restcomm.mscontrol.messages.JoinComplete;
import org.mobicents.servlet.restcomm.mscontrol.messages.JoinConference;
import org.mobicents.servlet.restcomm.mscontrol.messages.Leave;
import org.mobicents.servlet.restcomm.mscontrol.messages.MediaGroupCreated;
import org.mobicents.servlet.restcomm.mscontrol.messages.MediaGroupDestroyed;
import org.mobicents.servlet.restcomm.mscontrol.messages.MediaGroupResponse;
import org.mobicents.servlet.restcomm.mscontrol.messages.MediaGroupStateChanged;
import org.mobicents.servlet.restcomm.mscontrol.messages.MediaServerControllerError;
import org.mobicents.servlet.restcomm.mscontrol.messages.MediaServerControllerResponse;
import org.mobicents.servlet.restcomm.mscontrol.messages.MediaSessionClosed;
import org.mobicents.servlet.restcomm.mscontrol.messages.MediaSessionInfo;
import org.mobicents.servlet.restcomm.mscontrol.messages.Mute;
import org.mobicents.servlet.restcomm.mscontrol.messages.Play;
import org.mobicents.servlet.restcomm.mscontrol.messages.Record;
import org.mobicents.servlet.restcomm.mscontrol.messages.StartRecording;
import org.mobicents.servlet.restcomm.mscontrol.messages.Stop;
import org.mobicents.servlet.restcomm.mscontrol.messages.StopMediaGroup;
import org.mobicents.servlet.restcomm.mscontrol.messages.StopRecording;
import org.mobicents.servlet.restcomm.mscontrol.messages.Unmute;
import org.mobicents.servlet.restcomm.mscontrol.messages.UpdateMediaSession;
import org.mobicents.servlet.restcomm.patterns.Observe;
import org.mobicents.servlet.restcomm.patterns.Observing;
import org.mobicents.servlet.restcomm.patterns.StopObserving;
import org.mobicents.servlet.restcomm.util.WavUtils;

import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;

/**
 * @author Henrique Rosa (henrique.rosa@telestax.com)
 *
 */
public class XmsCallController extends MediaServerController {

    // Logging
    private final LoggingAdapter logger = Logging.getLogger(getContext().system(), this);

    // FSM.
    private final FiniteStateMachine fsm;

    // FSM states
    private final State uninitialized;
    private final State active;
    private final State inactive;
    private final State failed;

    // Intermediate FSM states
    private final State openingMediaSession;
    private final State updatingMediaSession;

    // JSR-309 runtime stuff
    private final MsControlFactory msControlFactory;
    private final MediaServerInfo mediaServerInfo;
    private MediaSession mediaSession;
    private NetworkConnection networkConnection;
    private MediaGroup mediaGroup;
    private MediaMixer mediaMixer;

    private final SdpListener sdpListener;
    private final PlayerListener playerListener;
    private final DtmfListener dtmfListener;
    private final RecorderListener recorderListener;

    // Call runtime stuff
    private ActorRef call;
    private Sid callId;
    private String localSdp;
    private String remoteSdp;
    private String connectionMode;
    private boolean callOutbound;

    // Conference runtime stuff
    private ActorRef bridge;
    private Boolean conferencing;

    // Call Media Operations
    private Sid accountId;
    private Sid recordingSid;
    private URI recordingUri;
    private Boolean recording;
    private Boolean playing;
    private Boolean collecting;
    private DateTime recordStarted;
    private DaoManager daoManager;

    // Runtime Setting
    private Configuration runtimeSettings;

    // Observers
    private final List<ActorRef> observers;

    public XmsCallController(MsControlFactory msControlFactory, MediaServerInfo mediaServerInfo) {
        super();
        final ActorRef source = self();

        // JSR-309 runtime stuff
        this.msControlFactory = msControlFactory;
        this.mediaServerInfo = mediaServerInfo;
        this.sdpListener = new SdpListener();
        this.playerListener = new PlayerListener();
        this.dtmfListener = new DtmfListener();
        this.recorderListener = new RecorderListener();

        // Initialize the states for the FSM
        this.uninitialized = new State("uninitialized", null, null);
        this.active = new State("active", new Active(source), null);
        this.inactive = new State("inactive", new Inactive(source), null);
        this.failed = new State("failed", new Failed(source), null);

        // Intermediate FSM states
        this.openingMediaSession = new State("opening media session", new OpeningMediaSession(source), null);
        this.updatingMediaSession = new State("updating media session", new UpdatingMediaSession(source), null);

        // Transitions for the FSM.
        final Set<Transition> transitions = new HashSet<Transition>();
        transitions.add(new Transition(uninitialized, openingMediaSession));
        // XXX the following transition is a quick fix for a concurrent issue between FSM and JSR 309 async API
        transitions.add(new Transition(uninitialized, active));
        transitions.add(new Transition(openingMediaSession, failed));
        transitions.add(new Transition(openingMediaSession, active));
        transitions.add(new Transition(openingMediaSession, inactive));
        transitions.add(new Transition(active, updatingMediaSession));
        transitions.add(new Transition(active, inactive));
        transitions.add(new Transition(updatingMediaSession, active));
        transitions.add(new Transition(updatingMediaSession, inactive));
        transitions.add(new Transition(updatingMediaSession, failed));

        // Finite state machine
        this.fsm = new FiniteStateMachine(this.uninitialized, transitions);

        // Observers
        this.observers = new ArrayList<ActorRef>();

        // Call runtime stuff
        this.localSdp = "";
        this.remoteSdp = "";
        this.callOutbound = false;
        this.conferencing = Boolean.FALSE;
        this.connectionMode = "inactive";
        this.recording = Boolean.FALSE;
        this.playing = Boolean.FALSE;
        this.collecting = Boolean.FALSE;
    }

    private boolean is(State state) {
        return fsm.state().equals(state);
    }

    private void notifyObservers(Object message, ActorRef self) {
        for (final ActorRef observer : observers) {
            observer.tell(message, self);
        }
    }

    /*
     * LISTENERS - MSCONTROL
     */
    private abstract class MediaListener<T extends MediaEvent<?>> implements MediaEventListener<T>, Serializable {

        private static final long serialVersionUID = 7103112381914312776L;

        protected ActorRef remote;

        public void setRemote(ActorRef sender) {
            this.remote = sender;
        }

    }

    private final class SdpListener extends MediaListener<SdpPortManagerEvent> {

        private static final long serialVersionUID = 1578203803932778931L;

        @Override
        public void onEvent(SdpPortManagerEvent event) {
            EventType eventType = event.getEventType();

            logger.info("********** Call Controller Current State: \"" + fsm.state().toString() + "\"");
            logger.info("********** Call Controller Processing Event: \"SdpPortManagerEvent\" (type = " + eventType + ")");

            try {
                if (event.isSuccessful()) {
                    networkConnection.getSdpPortManager().removeListener(this);
                    if (SdpPortManagerEvent.ANSWER_GENERATED.equals(eventType)) {
                        localSdp = new String(event.getMediaServerSdp());
                        fsm.transition(event, active);
                    } else if (SdpPortManagerEvent.OFFER_GENERATED.equals(eventType)) {
                        localSdp = new String(event.getMediaServerSdp());
                        fsm.transition(event, active);
                    } else if (SdpPortManagerEvent.ANSWER_PROCESSED.equals(eventType)) {
                        fsm.transition(event, active);
                    } else if (SdpPortManagerEvent.NETWORK_STREAM_FAILURE.equals(eventType)) {
                        throw new MsControlException("Network stream failure");
                    }
                } else {
                    throw new MsControlException("SDP processing failed");
                }
            } catch (Exception e) {
                call.tell(new MediaServerControllerError(e), self());
            }
        }

    }

    private final class PlayerListener extends MediaListener<PlayerEvent> {

        private static final long serialVersionUID = -1814168664061905439L;

        @Override
        public void onEvent(PlayerEvent event) {
            EventType eventType = event.getEventType();

            logger.info("********** Call Controller Current State: \"" + fsm.state().toString() + "\"");
            logger.info("********** Call Controller Processing Event: \"PlayerEvent\" (type = " + eventType + ")");

            if (PlayerEvent.PLAY_COMPLETED.equals(eventType)) {
                MediaGroupResponse<String> response;
                if (event.isSuccessful()) {
                    response = new MediaGroupResponse<String>(eventType.toString());
                } else {
                    String reason = event.getErrorText();
                    MediaServerControllerException error = new MediaServerControllerException(reason);
                    response = new MediaGroupResponse<String>(error, reason);
                }
                playing = Boolean.FALSE;
                super.remote.tell(response, self());
            }
        }

    }

    private final class DtmfListener extends MediaListener<SignalDetectorEvent> {

        private static final long serialVersionUID = -96652040901361098L;

        @Override
        public void onEvent(SignalDetectorEvent event) {
            EventType eventType = event.getEventType();

            logger.info("********** Call Controller Current State: \"" + fsm.state().toString() + "\"");
            logger.info("********** Call Controller Processing Event: \"SignalDetectorEvent\" (type = " + eventType + ")");

            if (SignalDetectorEvent.RECEIVE_SIGNALS_COMPLETED.equals(eventType)) {
                MediaGroupResponse<String> response;
                if (event.isSuccessful()) {
                    response = new MediaGroupResponse<String>(event.getSignalString());
                } else {
                    String reason = event.getErrorText();
                    MediaServerControllerException error = new MediaServerControllerException(reason);
                    response = new MediaGroupResponse<String>(error, reason);
                }
                collecting = Boolean.FALSE;
                super.remote.tell(response, self());
            }
        }

    }

    private final class RecorderListener extends MediaListener<RecorderEvent> {

        private static final long serialVersionUID = -8952464412809110917L;

        private String endOnKey = "";

        public void setEndOnKey(String endOnKey) {
            this.endOnKey = endOnKey;
        }

        @Override
        public void onEvent(RecorderEvent event) {
            EventType eventType = event.getEventType();

            logger.info("********** Call Controller Current State: \"" + fsm.state().toString() + "\"");
            logger.info("********** Call Controller Processing Event: \"RecorderEvent\" (type = " + eventType + ")");

            if (RecorderEvent.RECORD_COMPLETED.equals(eventType)) {
                MediaGroupResponse<String> response = null;
                if (event.isSuccessful()) {
                    String digits = "";
                    if (RecorderEvent.STOPPED.equals(event.getQualifier())) {
                        digits = endOnKey;
                    }
                    response = new MediaGroupResponse<String>(digits);
                } else {
                    String reason = event.getErrorText();
                    MediaServerControllerException error = new MediaServerControllerException(reason);
                    logger.error("Recording event failed: " + reason);
                    response = new MediaGroupResponse<String>(error, reason);
                }
                recording = Boolean.FALSE;
                super.remote.tell(response, self());
            }
        }

    }

    /*
     * EVENTS
     */
    @Override
    public void onReceive(Object message) throws Exception {
        final Class<?> klass = message.getClass();
        final ActorRef self = self();
        final ActorRef sender = sender();
        final State state = fsm.state();

        logger.info("********** Call Controller Current State: \"" + state.toString());
        logger.info("********** Call Controller Processing Message: \"" + klass.getName() + " sender : " + sender.getClass());

        if (Observe.class.equals(klass)) {
            onObserve((Observe) message, self, sender);
        } else if (StopObserving.class.equals(klass)) {
            onStopObserving((StopObserving) message, self, sender);
        } else if (CreateMediaSession.class.equals(klass)) {
            onCreateMediaSession((CreateMediaSession) message, self, sender);
        } else if (CloseMediaSession.class.equals(klass)) {
            onCloseMediaSession((CloseMediaSession) message, self, sender);
        } else if (UpdateMediaSession.class.equals(klass)) {
            onUpdateMediaSession((UpdateMediaSession) message, self, sender);
        } else if (CreateMediaGroup.class.equals(klass)) {
            onCreateMediaGroup((CreateMediaGroup) message, self, sender);
        } else if (DestroyMediaGroup.class.equals(klass)) {
            onDestroyMediaGroup((DestroyMediaGroup) message, self, sender);
        } else if (StopMediaGroup.class.equals(klass)) {
            onStopMediaGroup((StopMediaGroup) message, self, sender);
        } else if (Mute.class.equals(klass)) {
            onMute((Mute) message, self, sender);
        } else if (Unmute.class.equals(klass)) {
            onUnmute((Unmute) message, self, sender);
        } else if (StartRecording.class.equals(klass)) {
            onStartRecordingCall((StartRecording) message, self, sender);
        } else if (StopRecording.class.equals(klass)) {
            onStopRecordingCall((StopRecording) message, self, sender);
        } else if (Play.class.equals(klass)) {
            onPlay((Play) message, self, sender);
        } else if (Collect.class.equals(klass)) {
            onCollect((Collect) message, self, sender);
        } else if (Record.class.equals(klass)) {
            onRecord((Record) message, self, sender);
        } else if (JoinBridge.class.equals(klass)) {
            onJoinBridge((JoinBridge) message, self, sender);
        } else if (JoinConference.class.equals(klass)) {
            onJoinConference((JoinConference) message, self, sender);
        } else if (Stop.class.equals(klass)) {
            onStop((Stop) message, self, sender);
        } else if (Leave.class.equals(klass)) {
            onLeave((Leave) message, self, sender);
        }
    }

    private void onObserve(Observe message, ActorRef self, ActorRef sender) throws Exception {
        final ActorRef observer = message.observer();
        if (observer != null) {
            synchronized (this.observers) {
                this.observers.add(observer);
                observer.tell(new Observing(self), self);
            }
        }
    }

    private void onStopObserving(StopObserving message, ActorRef self, ActorRef sender) throws Exception {
        final ActorRef observer = message.observer();
        if (observer != null) {
            this.observers.remove(observer);
        } else {
            this.observers.clear();
        }
    }

    private void onCreateMediaSession(CreateMediaSession message, ActorRef self, ActorRef sender) throws Exception {
        if (is(uninitialized)) {
            this.call = sender;
            this.callOutbound = message.isOutbound();
            this.connectionMode = message.getConnectionMode();
            this.remoteSdp = message.getSessionDescription();

            fsm.transition(message, openingMediaSession);
        }
    }

    private void onCloseMediaSession(CloseMediaSession message, ActorRef self, ActorRef sender) throws Exception {
        if (is(active) || is(openingMediaSession) || is(updatingMediaSession)) {
            fsm.transition(message, inactive);
        }
    }

    private void onUpdateMediaSession(UpdateMediaSession message, ActorRef self, ActorRef sender) throws Exception {
        if (is(active)) {
            this.remoteSdp = message.getSessionDescription();
            fsm.transition(message, updatingMediaSession);
        }
    }

    private void onCreateMediaGroup(CreateMediaGroup message, ActorRef self, ActorRef sender) {
        // Always reuse current media group if active
        if (this.mediaGroup == null) {
            // Create new media group
            try {
                this.mediaGroup = this.mediaSession.createMediaGroup(MediaGroup.PLAYER_RECORDER_SIGNALDETECTOR);

                // Prepare the Media Group resources
                this.mediaGroup.getPlayer().addListener(this.playerListener);
                this.mediaGroup.getSignalDetector().addListener(this.dtmfListener);
                this.mediaGroup.getRecorder().addListener(this.recorderListener);

                // Initialize Media Group
                this.networkConnection.join(Direction.DUPLEX, this.mediaGroup);

                // Warn call the media group has been created
                final MediaGroupCreated mgCreated = new MediaGroupCreated();
                sender.tell(new MediaServerControllerResponse<MediaGroupCreated>(mgCreated), self);
            } catch (MsControlException e) {
                // Warn call the media group could not be created
                sender.tell(new MediaServerControllerError(e), self);
            }
        }
    }

    private void onDestroyMediaGroup(DestroyMediaGroup message, ActorRef self, ActorRef sender) {
        if (this.mediaGroup != null) {
            // Destroy media group
            this.mediaGroup.release();
            this.mediaGroup = null;
        }

        // XXX always send this message (may be null in bridged calls)
        // Warn call the media group has been destroyed
        final MediaGroupDestroyed mgDestroyed = new MediaGroupDestroyed();
        this.call.tell(new MediaServerControllerResponse<MediaGroupDestroyed>(mgDestroyed), self);
    }

    private void onStopMediaGroup(StopMediaGroup message, ActorRef self, ActorRef sender) throws MsControlException {
        try {
            if (this.mediaGroup != null) {
                // XXX mediaGroup.stop() not implemented on dialogic connector
                if (this.playing) {
                    this.mediaGroup.getPlayer().stop(true);
                    this.playing = Boolean.FALSE;
                }

                if (this.recording) {
                    this.mediaGroup.getRecorder().stop();
                    this.recording = Boolean.FALSE;
                }

                if (this.collecting) {
                    this.mediaGroup.getSignalDetector().stop();
                    this.collecting = Boolean.FALSE;
                }
            }

            // Tell observers the media group has been stopped
            final MediaGroupStateChanged response = new MediaGroupStateChanged(MediaGroupStateChanged.State.INACTIVE);
            notifyObservers(response, self);
        } catch (MsControlException e) {
            call.tell(new MediaServerControllerError(e), self);
        }
    }

    private void onMute(Mute message, ActorRef self, ActorRef sender) {
        if (is(active)) {
            try {
                if (this.mediaMixer != null) {
                    this.networkConnection.join(Direction.RECV, this.mediaMixer);
                }
            } catch (MsControlException e) {
                logger.error("Could not mute call: " + e.getMessage(), e);
            }
        }
    }

    private void onUnmute(Unmute message, ActorRef self, ActorRef sender) throws Exception {
        if (is(active)) {
            try {
                if (this.mediaMixer != null) {
                    this.networkConnection.join(Direction.DUPLEX, this.mediaMixer);
                }
            } catch (MsControlException e) {
                logger.error("Could not unmute call: " + e.getMessage(), e);
            }
        }
    }

    private void onStartRecordingCall(StartRecording message, ActorRef self, ActorRef sender) {
        if (is(active)) {
            if (runtimeSettings == null) {
                this.runtimeSettings = message.getRuntimeSetting();
            }

            if (daoManager == null) {
                daoManager = message.getDaoManager();
            }

            if (accountId == null) {
                accountId = message.getAccountId();
            }

            this.callId = message.getCallId();
            this.recordingSid = message.getRecordingSid();
            this.recordingUri = message.getRecordingUri();
            this.recording = true;

            logger.info("Start recording call");
            this.recordStarted = DateTime.now();

            // Tell media group to start recording
            final Record record = new Record(recordingUri, 5, 3600, "1234567890*#");
            onRecord(record, self, sender);
        }
    }

    private void onStopRecordingCall(StopRecording message, ActorRef self, ActorRef sender) {
        if (is(active) && recording) {
            if (runtimeSettings == null) {
                this.runtimeSettings = message.getRuntimeSetting();
            }

            if (daoManager == null) {
                this.daoManager = message.getDaoManager();
            }

            if (accountId == null) {
                this.accountId = message.getAccountId();
            }

            // Tell media group to stop recording
            logger.info("Stop recording call");
            onStop(new Stop(false), self, sender);
        }
    }

    private void onPlay(Play message, ActorRef self, ActorRef sender) {
        if (is(active)) {
            try {
                List<URI> uris = message.uris();
                Parameters params = this.mediaGroup.createParameters();
                int repeatCount = message.iterations() <= 0 ? Player.FOREVER : message.iterations() - 1;
                params.put(Player.REPEAT_COUNT, repeatCount);
                this.playerListener.setRemote(sender);
                this.mediaGroup.getPlayer().play(uris.toArray(new URI[uris.size()]), RTC.NO_RTC, params);
                this.playing = Boolean.TRUE;
            } catch (MsControlException e) {
                logger.error("Play failed: " + e.getMessage());
                final MediaGroupResponse<String> response = new MediaGroupResponse<String>(e);
                notifyObservers(response, self);
            }
        }
    }

    private void onCollect(Collect message, ActorRef self, ActorRef sender) {
        if (is(active)) {
            try {
                Parameters optargs = this.mediaGroup.createParameters();

                // Add patterns to the detector
                List<Parameter> patterns = new ArrayList<Parameter>(2);
                if (message.hasEndInputKey()) {
                    optargs.put(SignalDetector.PATTERN[0], message.endInputKey());
                    patterns.add(SignalDetector.PATTERN[0]);
                }

                if (message.hasPattern()) {
                    optargs.put(SignalDetector.PATTERN[1], message.pattern());
                    patterns.add(SignalDetector.PATTERN[1]);
                }

                Parameter[] patternArray = null;
                if (!patterns.isEmpty()) {
                    patternArray = patterns.toArray(new Parameter[patterns.size()]);
                }

                // Setup enabled events
                EventType[] enabledEvents = { SignalDetectorEvent.RECEIVE_SIGNALS_COMPLETED };
                optargs.put(SignalDetector.ENABLED_EVENTS, enabledEvents);

                // Setup prompts
                if (message.hasPrompts()) {
                    List<URI> prompts = message.prompts();
                    optargs.put(SignalDetector.PROMPT, prompts.toArray(new URI[prompts.size()]));
                }

                // Setup time out interval
                int timeout = message.timeout();
                optargs.put(SignalDetector.INITIAL_TIMEOUT, timeout);
                optargs.put(SignalDetector.INTER_SIG_TIMEOUT, timeout);

                // Disable buffering for performance gain
                optargs.put(SignalDetector.BUFFERING, false);

                this.dtmfListener.setRemote(sender);
                this.mediaGroup.getSignalDetector().flushBuffer();
                this.mediaGroup.getSignalDetector().receiveSignals(message.numberOfDigits(), patternArray, RTC.NO_RTC, optargs);
                this.collecting = Boolean.TRUE;
            } catch (MsControlException e) {
                logger.error("DTMF recognition failed: " + e.getMessage());
                final MediaGroupResponse<String> response = new MediaGroupResponse<String>(e);
                notifyObservers(response, self);
            }
        }
    }

    private void onRecord(Record message, ActorRef self, ActorRef sender) {
        if (is(active)) {
            try {
                Parameters params = this.mediaGroup.createParameters();

                // Add prompts
                if (message.hasPrompts()) {
                    List<URI> prompts = message.prompts();
                    // TODO JSR-309 connector still does not support multiple prompts
                    // params.put(Recorder.PROMPT, prompts.toArray(new URI[prompts.size()]));
                    params.put(Recorder.PROMPT, prompts.get(0));
                }

                // Finish on key
                RTC[] rtcs;
                if (message.hasEndInputKey()) {
                    params.put(SignalDetector.PATTERN[0], message.endInputKey());
                    params.put(SignalDetector.INTER_SIG_TIMEOUT, new Integer(10000));
                    rtcs = new RTC[] { MediaGroup.SIGDET_STOPPLAY };
                } else {
                    rtcs = RTC.NO_RTC;
                }

                // Recording length
                params.put(Recorder.MAX_DURATION, message.length() * 1000);

                // Recording timeout
                int timeout = message.timeout();
                params.put(SpeechDetectorConstants.INITIAL_TIMEOUT, timeout);
                params.put(SpeechDetectorConstants.FINAL_TIMEOUT, timeout);

                // Other parameters
                params.put(Recorder.APPEND, Boolean.FALSE);
                // TODO set as definitive media group parameter - handled by RestComm
                params.put(Recorder.START_BEEP, Boolean.FALSE);

                this.recorderListener.setEndOnKey(message.endInputKey());
                this.recorderListener.setRemote(sender);
                this.mediaGroup.getRecorder().record(message.destination(), rtcs, params);
                this.recording = Boolean.TRUE;
            } catch (MsControlException e) {
                logger.error("Recording failed: " + e.getMessage());
                final MediaGroupResponse<String> response = new MediaGroupResponse<String>(e);
                notifyObservers(response, self);
            }
        }
    }

    private void onJoinBridge(JoinBridge message, ActorRef self, ActorRef sender) {
        if (is(active)) {
            try {
                // join call leg to bridge
                this.bridge = sender;
                this.mediaMixer = (MediaMixer) message.getEndpoint();
                this.networkConnection.join(Direction.DUPLEX, mediaMixer);

                // alert conference call has joined successfully
                this.call.tell(new JoinComplete(), self);
            } catch (MsControlException e) {
                logger.error("Call bridging failed: " + e.getMessage());
                final MediaGroupResponse<String> response = new MediaGroupResponse<String>(e);
                notifyObservers(response, self);
            }
        }
    }

    private void onJoinConference(JoinConference message, ActorRef self, ActorRef sender) {
        if (is(active)) {
            try {
                // join call leg to bridge
                this.bridge = sender;
                this.mediaMixer = (MediaMixer) message.getEndpoint();
                this.networkConnection.join(Direction.DUPLEX, mediaMixer);

                // alert conference call has joined successfully
                this.call.tell(new JoinComplete(), self);
            } catch (MsControlException e) {
                logger.error("Call bridging failed: " + e.getMessage());
                final MediaGroupResponse<String> response = new MediaGroupResponse<String>(e);
                notifyObservers(response, self);
            }
        }
    }

    private void onStop(Stop message, ActorRef self, ActorRef sender) {
        try {
            // XXX mediaGroup.stop() not implemented on dialogic connector
            if (this.playing) {
                this.mediaGroup.getPlayer().stop(true);
                this.playing = Boolean.FALSE;
            }

            if (this.recording) {
                this.mediaGroup.getRecorder().stop();
                this.recording = Boolean.FALSE;

                if (message.createRecord() && recordingUri != null) {
                    Double duration;
                    try {
                        duration = WavUtils.getAudioDuration(recordingUri);
                    } catch (UnsupportedAudioFileException | IOException e) {
                        logger.error("Could not measure recording duration: " + e.getMessage(), e);
                        duration = 0.0;
                    }
                    if (duration.equals(0.0)) {
                        logger.info("Call wraping up recording. File doesn't exist since duration is 0");
                        final DateTime end = DateTime.now();
                        duration = new Double((end.getMillis() - recordStarted.getMillis()) / 1000);
                    } else {
                        logger.info("Call wraping up recording. File already exists, length: "
                                + (new File(recordingUri).length()));
                    }
                    final Recording.Builder builder = Recording.builder();
                    builder.setSid(recordingSid);
                    builder.setAccountSid(accountId);
                    builder.setCallSid(callId);
                    builder.setDuration(duration);
                    builder.setApiVersion(runtimeSettings.getString("api-version"));
                    StringBuilder buffer = new StringBuilder();
                    buffer.append("/").append(runtimeSettings.getString("api-version")).append("/Accounts/")
                            .append(accountId.toString());
                    buffer.append("/Recordings/").append(recordingSid.toString());
                    builder.setUri(URI.create(buffer.toString()));
                    final Recording recording = builder.build();
                    RecordingsDao recordsDao = daoManager.getRecordingsDao();
                    recordsDao.addRecording(recording);
                }
            }

            if (this.collecting) {
                this.mediaGroup.getSignalDetector().stop();
                this.collecting = Boolean.FALSE;
            }
        } catch (MsControlException e) {
            call.tell(new MediaServerControllerError(e), self);
        }
    }

    private void onLeave(Leave message, ActorRef self, ActorRef sender) {
        if (is(active) && this.conferencing) {
            try {
                networkConnection.unjoin(mediaMixer);
                mediaMixer = null;
                conferencing = Boolean.FALSE;
            } catch (MsControlException e) {
                call.tell(new MediaServerControllerError(e), self);
            }
        }
    }

    /*
     * ACTIONS
     */
    private final class OpeningMediaSession extends AbstractAction {

        public OpeningMediaSession(ActorRef source) {
            super(source);
        }

        @Override
        public void execute(Object message) throws Exception {
            try {
                // Create media session
                mediaSession = msControlFactory.createMediaSession();

                // Create network connection
                networkConnection = mediaSession.createNetworkConnection(NetworkConnection.BASIC);
                networkConnection.getSdpPortManager().addListener(sdpListener);
                if (callOutbound) {
                    networkConnection.getSdpPortManager().generateSdpOffer();
                } else {
                    networkConnection.getSdpPortManager().processSdpOffer(remoteSdp.getBytes());
                }
            } catch (MsControlException e) {
                sender().tell(new MediaServerControllerError(e), super.source);
            }
        }

    }

    private final class UpdatingMediaSession extends AbstractAction {

        public UpdatingMediaSession(ActorRef source) {
            super(source);
        }

        @Override
        public void execute(Object message) throws Exception {
            try {
                networkConnection.getSdpPortManager().addListener(sdpListener);
                networkConnection.getSdpPortManager().processSdpAnswer(remoteSdp.getBytes());
            } catch (MsControlException e) {
                sender().tell(new MediaServerControllerError(e), super.source);
            }
        }

    }

    private final class Active extends AbstractAction {

        public Active(ActorRef source) {
            super(source);
        }

        @Override
        public void execute(Object message) throws Exception {
            final MediaSessionInfo info = new MediaSessionInfo(true, mediaServerInfo.getAddress(), localSdp, remoteSdp);
            call.tell(new MediaServerControllerResponse<MediaSessionInfo>(info), super.source);
        }

    }

    private final class Inactive extends AbstractAction {

        public Inactive(ActorRef source) {
            super(source);
        }

        @Override
        public void execute(Object message) throws Exception {
            if (mediaSession != null) {
                mediaSession.release();
                mediaSession = null;
                mediaGroup = null;
            }

            // Inform call that media session has been properly closed
            final MediaSessionClosed response = new MediaSessionClosed();
            call.tell(new MediaServerControllerResponse<MediaSessionClosed>(response), super.source);
        }

    }

    private final class Failed extends AbstractAction {

        public Failed(ActorRef source) {
            super(source);
        }

        @Override
        public void execute(Object message) throws Exception {

            if (message instanceof SdpPortManagerEvent) {
                SdpPortManagerEvent event = (SdpPortManagerEvent) message;
                logger.warning("XMS returned error: " + event.getErrorText() + ". Failing call...");
            }

            // Inform call the media session could not be set up
            final MediaServerControllerError error = new MediaServerControllerError();
            call.tell(error, super.source);
        }

    }

}
