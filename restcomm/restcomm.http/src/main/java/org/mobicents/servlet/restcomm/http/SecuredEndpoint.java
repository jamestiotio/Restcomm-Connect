/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2014, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 *
 */
package org.mobicents.servlet.restcomm.http;

import java.util.Set;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.lang.NotImplementedException;
import org.apache.log4j.Logger;
import org.apache.shiro.authz.AuthorizationException;
import org.apache.shiro.authz.Permission;
import org.apache.shiro.authz.SimpleRole;
import org.apache.shiro.authz.permission.WildcardPermissionResolver;
import org.mobicents.servlet.restcomm.dao.AccountsDao;
import org.mobicents.servlet.restcomm.dao.DaoManager;
import org.mobicents.servlet.restcomm.entities.Account;
import org.mobicents.servlet.restcomm.entities.Sid;
import org.mobicents.servlet.restcomm.identity.AuthOutcome;
import org.mobicents.servlet.restcomm.identity.IdentityContext;
import org.mobicents.servlet.restcomm.identity.UserIdentityContext;
import org.mobicents.servlet.restcomm.identity.shiro.RestcommRoles;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;


/**
 * Security layer endpoint. It will scan the request for security related assets and populate the
 * UserIdentityContext accordingly. Extend the class and use secure*() methods to apply security rules to
 * your endpoint.
 *
 * How to use it:
 * - use secure() method to check that a user (any user) is authenticated.
 * - use secure(permission) method to check that an authenticated user has the required permission according to his roles
 * - use secure(account,permission) method to check that besides permission a user also has ownership over an account
 *
 * @author orestis.tsakiridis@telestax.com (Orestis Tsakiridis)
 */
public abstract class SecuredEndpoint extends AbstractEndpoint {

    // types of secured resources used to apply different policies to applications, numbers etc.
    public enum SecuredType {
        SECURED_APP,
        SECURED_ACCOUNT, SECURED_STANDARD
    }

    protected Logger logger = Logger.getLogger(SecuredEndpoint.class);

    protected UserIdentityContext userIdentityContext;
    protected AccountsDao accountsDao;
    protected IdentityContext identityContext;
    @Context
    protected ServletContext context;
    @Context
    HttpServletRequest request;

    public SecuredEndpoint() {
        super();
    }

    protected void init(final Configuration configuration) {
        super.init(configuration);
        final DaoManager storage = (DaoManager) context.getAttribute(DaoManager.class.getName());
        this.accountsDao = storage.getAccountsDao();
        this.identityContext = (IdentityContext) context.getAttribute(IdentityContext.class.getName());
        this.userIdentityContext = new UserIdentityContext(request, accountsDao);
    }

    /**
     * Grants general purpose access if any valid token exists in the request
     */
    protected void secure() {
        if (userIdentityContext.getEffectiveAccount() == null)
            throw new AuthorizationException();
    }

    // boolean overloaded form of secure()
    protected boolean isSecured() {
        try {
            secure();
            return true;
        } catch (AuthorizationException e) {
            return false;
        }
    }

    /**
     * Grants access by permission. If the effective account has a role that resolves
     * to the specified permission (accoording to mappings of restcomm.xml) access is granted.
     * Administrator is granted access regardless of permissions.
     *
     * @param permission - e.g. 'RestComm:Create:Accounts'
     */
    protected void secure (final String permission) {
        secure(); // ok there is a valid authenticated account
        if ( secureApi(permission, userIdentityContext.getEffectiveAccountRoles()) != AuthOutcome.OK )
            throw new AuthorizationException();
    }

    // boolean overloaded form of secure(permission)
    protected boolean isSecured(final String permission) {
        try {
            secure(permission);
            return true;
        } catch (AuthorizationException e) {
            return false;
        }
    }

    // Authorize request by using either keycloak token or API key method. If any of them succeeds, request is allowed
    /**
     * High level authorization. It grants access to 'account' resources required by permission.
     * It takes into account any Oauth token of API Key existing in the request.
     * @param operatedAccount
     * @param permission
     * @throws AuthorizationException
     */
    /**
     * Personalized type of grant. Besides checking 'permission' the effective account should have some sort of
     * ownership over the operatedAccount. The exact type of ownership is defined in secureAccount()
     *
     * @param operatedAccount
     * @param permission
     * @throws AuthorizationException
     */
    protected void secure(final Account operatedAccount, final String permission) throws AuthorizationException {
        secure(operatedAccount, permission, SecuredType.SECURED_STANDARD);
    }

    protected void secure(final Account operatedAccount, final String permission, SecuredType type) throws AuthorizationException {
        if (operatedAccount == null)
            throw new AuthorizationException();
        secure(permission); // check an authbenticated account allowed to do "permission" is available
        if (type == SecuredType.SECURED_STANDARD) {
            if (secureLevelControl(userIdentityContext.getEffectiveAccount(), operatedAccount, null) != AuthOutcome.OK )
                throw new AuthorizationException();
        } else
        if (type == SecuredType.SECURED_APP) {
            if (secureLevelControlApplications(userIdentityContext.getEffectiveAccount(),operatedAccount,null) != AuthOutcome.OK)
                throw new AuthorizationException();
        } else
        if (type == SecuredType.SECURED_ACCOUNT) {
            if (secureLevelControlAccounts(userIdentityContext.getEffectiveAccount(), operatedAccount) != AuthOutcome.OK)
                throw new AuthorizationException();
        }
    }

    // boolean overloaded form of secure(operatedAccount, String permission)
    protected boolean isSecured(final Account operatedAccount, final String permission) {
        try {
            secure(operatedAccount, permission);
            return true;
        } catch (AuthorizationException e) {
            return false;
        }
    }

    protected void secure(final Account operatedAccount, final Sid resourceAccountSid, SecuredType type) throws AuthorizationException {
        String resourceAccountSidString = resourceAccountSid == null ? null : resourceAccountSid.toString();
        if (type == SecuredType.SECURED_APP) {
            if (secureLevelControlApplications(userIdentityContext.getEffectiveAccount(), operatedAccount, resourceAccountSidString) != AuthOutcome.OK)
                throw new AuthorizationException();
        } else
        if (type == SecuredType.SECURED_STANDARD){
            if (secureLevelControl(userIdentityContext.getEffectiveAccount(), operatedAccount, resourceAccountSidString) != AuthOutcome.OK)
                throw new AuthorizationException();
        } else
        if (type == SecuredType.SECURED_ACCOUNT)
            throw new IllegalStateException("Account security is not supported when using sub-resources");
        else {
            throw new NotImplementedException();
        }
    }

    protected void secure(final Account operatedAccount, final Sid resourceAccountSid, final String permission) throws AuthorizationException {
        secure(operatedAccount, resourceAccountSid, permission, SecuredType.SECURED_STANDARD);
    }

    protected void secure(final Account operatedAccount, final Sid resourceAccountSid, final String permission, final SecuredType type ) {
        secure(operatedAccount, resourceAccountSid, type);
        secure(permission); // check an authbenticated account allowed to do "permission" is available
    }

    /**
     * Checks is the effective account has the specified role. Only role values contained in the Restcomm Account
     * are take into account.
     *
     * @param role
     * @return true if the role exists in the Account. Otherwise it returns false.
     */
    protected boolean hasAccountRole(final String role) {
        if (userIdentityContext.getEffectiveAccount() != null) {
            return userIdentityContext.getEffectiveAccountRoles().contains(role);
        }
        return false;
    }

    /**
     * Low level permission checking. roleNames are checked for neededPermissionString permission using permission
     * mappings contained in restcomm.xml. The permission mappings are stored in RestcommRoles.
     *
     * Note: Administrator is granted access with eyes closed

     * @param neededPermissionString
     * @param roleNames
     * @return
     */
    private AuthOutcome secureApi(String neededPermissionString, Set<String> roleNames) {
        // if this is an administrator ask no more questions
        if ( roleNames.contains(getAdministratorRole()))
            return AuthOutcome.OK;

        // normalize the permission string
        neededPermissionString = "domain:" + neededPermissionString;

        WildcardPermissionResolver resolver = new WildcardPermissionResolver();
        Permission neededPermission = resolver.resolvePermission(neededPermissionString);

        // check the neededPermission against all roles of the user
        RestcommRoles restcommRoles = identityContext.getRestcommRoles();
        for (String roleName: roleNames) {
            SimpleRole simpleRole = restcommRoles.getRole(roleName);
            if ( simpleRole == null) {
                // logger.warn("Cannot map keycloak role '" + roleName + "' to local restcomm configuration. Ignored." );
            }
            else {
                Set<Permission> permissions = simpleRole.getPermissions();
                // check the permissions one by one
                for (Permission permission: permissions) {
                    if (permission.implies(neededPermission)) {
                        logger.debug("Granted access by permission " + permission.toString());
                        return AuthOutcome.OK;
                    }
                }
                logger.debug("Role " + roleName + " does not allow " + neededPermissionString);
            }
        }
        return AuthOutcome.FAILED;
    }

    /**
     * Makes sure a user authenticated as actorAccount can access operatedAccount. In practice allows access if actorAccount == operatedAccount
     * OR (UNDER REVIEW) if operatedAccount is a sub-account of actorAccount
     *
     * UPDATE: parent-child relation check is disabled for compatibility reasons.
     *
     * @param actorAccount
     * @param operatedAccount
     * @return
     */
//
//    private AuthOutcome secureAccount(Account actorAccount, final Account operatedAccount) {
//        if ( actorAccount != null && actorAccount.getSid() != null ) {
//            if ( actorAccount.getSid().equals(operatedAccount.getSid()) /*|| actorAccount.getSid().equals(operatedAccount.getAccountSid()) */ ) {
//                return AuthOutcome.OK;
//            }
//        }
//        return AuthOutcome.FAILED;
//    }

    /**
     * Applies the following access control rule:

     * If no sub-resources are involved (resourceAccountSid is null):
     *  - If operatingAccount is the same or a parent of operatedAccount access is granted
     * If there are sub-resources involved:
     *  - If operatingAccount is the same or a parent of operatedAccount AND resoulrce belongs to operatedAccount access is granted

     * @param operatingAccount  the account that is authenticated
     * @param operatedAccount the account specified in the URL
     * @param resourceAccountSid the account SID property of the operated resource e.g. the accountSid of a DID.
     *
     */
    protected AuthOutcome secureLevelControl( Account operatingAccount, Account operatedAccount, String resourceAccountSid) {
        String operatingAccountSid = null;
        if (operatingAccount != null)
            operatingAccountSid = operatingAccount.getSid().toString();
        String operatedAccountSid = null;
        if (operatedAccount != null)
            operatedAccountSid = operatedAccount.getSid().toString();

        if (!operatingAccountSid.equals(operatedAccountSid)) {
            Account account = accountsDao.getAccount(new Sid(operatedAccountSid));
            if (!operatingAccountSid.equals(String.valueOf(account.getAccountSid()))) {
                return AuthOutcome.FAILED;
            } else if (resourceAccountSid != null && !operatedAccountSid.equals(resourceAccountSid)) {
                return AuthOutcome.FAILED;
            }
        } else if (resourceAccountSid != null && !operatingAccountSid.equals(resourceAccountSid)) {
            return AuthOutcome.FAILED;
        }
        return AuthOutcome.OK;
    }


    protected AuthOutcome secureLevelControlApplications(Account operatingAccount, Account operatedAccount, String applicationAccountSid) {
        String operatingAccountSid = null;
        if (operatingAccount != null)
            operatingAccountSid = operatingAccount.getSid().toString();
        String operatedAccountSid = null;
        if (operatedAccount != null)
            operatedAccountSid = operatedAccount.getSid().toString();

        if (!operatingAccountSid.equals(String.valueOf(operatedAccountSid))) {
            return AuthOutcome.FAILED;
        } else if (applicationAccountSid != null && !operatingAccountSid.equals(applicationAccountSid)) {
            return AuthOutcome.FAILED;
        }
        return AuthOutcome.OK;
    }

    private AuthOutcome secureLevelControlAccounts(Account operatingAccount, Account operatedAccount) {
        if (operatingAccount == null || operatedAccount == null)
            return AuthOutcome.FAILED;
        if (getAdministratorRole().equals(operatingAccount.getRole())) {
            // administrator can also operate on child accounts
            if (!String.valueOf(operatingAccount.getSid()).equals(String.valueOf(operatedAccount.getSid()))) {
                if (!String.valueOf(operatingAccount.getSid()).equals(String.valueOf(operatedAccount.getAccountSid()))) {
                    return AuthOutcome.FAILED;
                }
            }
        } else { // non-administrators

            if ( operatingAccount.getSid().equals(operatedAccount.getAccountSid()) )
                return AuthOutcome.OK;
            else
                return AuthOutcome.FAILED;
        }
        return AuthOutcome.OK;
    }

    /**
     * Returns the string literal for the administrator role. This role is granted implicitly access from secure() method.
     * No need to explicitly apply it at each protected resource
     * .
     * @return the administrator role as string
     */
    protected String getAdministratorRole() {
        return "Administrator";
    }

}
