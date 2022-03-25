package org.exoplatform.processes.notification.provider;

import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.NotificationMessageUtils;
import org.exoplatform.commons.api.notification.annotation.TemplateConfig;
import org.exoplatform.commons.api.notification.annotation.TemplateConfigs;
import org.exoplatform.commons.api.notification.channel.template.AbstractTemplateBuilder;
import org.exoplatform.commons.api.notification.channel.template.TemplateProvider;
import org.exoplatform.commons.api.notification.model.MessageInfo;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.api.notification.model.PluginKey;
import org.exoplatform.commons.api.notification.service.template.TemplateContext;
import org.exoplatform.commons.notification.template.TemplateUtils;
import org.exoplatform.commons.utils.HTMLEntityEncoder;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.processes.notification.plugin.CancelRequestPlugin;
import org.exoplatform.processes.notification.plugin.CreateRequestPlugin;
import org.exoplatform.processes.notification.utils.NotificationArguments;
import org.exoplatform.processes.notification.utils.NotificationUtils;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.notification.LinkProviderUtils;
import org.exoplatform.webui.utils.TimeConvertUtils;

import java.io.Writer;
import java.util.Calendar;
import java.util.Locale;

@TemplateConfigs(templates = {
    @TemplateConfig(pluginId = CreateRequestPlugin.ID, template = "war:/notification/templates/mail/CreateRequestPlugin.gtmpl"),
    @TemplateConfig(pluginId = CancelRequestPlugin.ID, template = "war:/notification/templates/mail/CancelRequestPlugin.gtmpl"), })
public class MailTemplateProvider extends TemplateProvider {

  private IdentityManager identityManager;

  public MailTemplateProvider(InitParams initParams, IdentityManager identityManager) {
    super(initParams);
    this.identityManager = identityManager;
    this.templateBuilders.put(PluginKey.key(CreateRequestPlugin.ID), new TemplateBuilder());
    this.templateBuilders.put(PluginKey.key(CancelRequestPlugin.ID), new TemplateBuilder());
  }

  private class TemplateBuilder extends AbstractTemplateBuilder {

    @Override
    protected MessageInfo makeMessage(NotificationContext notificationContext) {
      NotificationInfo notificationInfo = notificationContext.getNotificationInfo();
      String pluginId = notificationInfo.getKey().getId();

      String language = getLanguage(notificationInfo);
      HTMLEntityEncoder encoder = HTMLEntityEncoder.getInstance();
      TemplateContext templateContext = TemplateContext.newChannelInstance(getChannelKey(), pluginId, language);
      String requester = notificationInfo.getValueOwnerParameter(NotificationArguments.REQUEST_CREATOR.getKey());
      String processUrl = notificationInfo.getValueOwnerParameter(NotificationArguments.PROCESS_URL.getKey());
      String requestUrl = notificationInfo.getValueOwnerParameter(NotificationArguments.REQUEST_URL.getKey());
      Profile userProfile = NotificationUtils.getUserProfile(requester);
      templateContext.put("USER", encoder.encode(userProfile.getFullName()));
      templateContext.put("PROFILE_URL", encoder.encode(userProfile.getUrl()));
      if (pluginId.equals(CreateRequestPlugin.ID)) {
        String requestTitle = notificationInfo.getValueOwnerParameter(NotificationArguments.REQUEST_TITLE.getKey());
        String requestDescription = notificationInfo.getValueOwnerParameter(NotificationArguments.REQUEST_DESCRIPTION.getKey());
        String processTitle = notificationInfo.getValueOwnerParameter(NotificationArguments.REQUEST_PROCESS.getKey());
        templateContext.put("PROCESS_TITLE", encoder.encode(processTitle));
        templateContext.put("REQUEST_TITLE", encoder.encode(requestTitle));
        templateContext.put("REQUEST_DESCRIPTION", encoder.encode(requestDescription));
      }
      templateContext.put("PROCESS_URL", encoder.encode(processUrl));
      templateContext.put("REQUEST_URL", encoder.encode(requestUrl));
      templateContext.put("AVATAR", encoder.encode(LinkProviderUtils.getUserAvatarUrl(userProfile)));

      Calendar lastModified = Calendar.getInstance();
      lastModified.setTimeInMillis(notificationInfo.getLastModifiedDate());
      templateContext.put("LAST_UPDATED_TIME",
                          TimeConvertUtils.convertXTimeAgoByTimeServer(lastModified.getTime(),
                                                                       "EE, dd yyyy",
                                                                       new Locale(language),
                                                                       TimeConvertUtils.YEAR));
      boolean isRead =
                     Boolean.parseBoolean(notificationInfo.getValueOwnerParameter(NotificationMessageUtils.READ_PORPERTY.getKey()));
      templateContext.put("READ", isRead ? "read" : "unread");
      templateContext.put("NOTIFICATION_ID", notificationInfo.getId());

      Identity receiver = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, notificationInfo.getTo());
      if (receiver == null || receiver.getRemoteId().equals(notificationInfo.getFrom())) {
        return null;
      }
      templateContext.put("FIRST_NAME", encoder.encode(receiver.getProfile().getProperty(Profile.FIRST_NAME).toString()));
      // Footer
      templateContext.put("FOOTER_LINK", LinkProviderUtils.getRedirectUrl("notification_settings", receiver.getRemoteId()));
      String subject = TemplateUtils.processSubject(templateContext);
      String body = TemplateUtils.processGroovy(templateContext);

      notificationContext.setException(templateContext.getException());
      MessageInfo messageInfo = new MessageInfo();
      return messageInfo.subject(subject).body(body).end();
    }

    @Override
    protected boolean makeDigest(NotificationContext notificationContext, Writer writer) {
      return false;
    }
  }
}
