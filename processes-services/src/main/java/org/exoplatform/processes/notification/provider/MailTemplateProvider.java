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
import org.exoplatform.processes.notification.plugin.RequestCommentPlugin;
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
    @TemplateConfig(pluginId = CancelRequestPlugin.ID, template = "war:/notification/templates/mail/CancelRequestPlugin.gtmpl"),
    @TemplateConfig(pluginId = RequestCommentPlugin.ID, template = "war:/notification/templates/mail/RequestCommentPlugin.gtmpl"),})
public class MailTemplateProvider extends TemplateProvider {

  private IdentityManager identityManager;

  public MailTemplateProvider(InitParams initParams, IdentityManager identityManager) {
    super(initParams);
    this.identityManager = identityManager;
    this.templateBuilders.put(PluginKey.key(CreateRequestPlugin.ID), new TemplateBuilder());
    this.templateBuilders.put(PluginKey.key(CancelRequestPlugin.ID), new TemplateBuilder());
    this.templateBuilders.put(PluginKey.key(RequestCommentPlugin.ID), new RequestCommentTemplateBuilder());
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
      buildCommonTemplateParams(templateContext, notificationInfo, language, requester, encoder);
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

  private class RequestCommentTemplateBuilder extends AbstractTemplateBuilder {

    @Override
    protected MessageInfo makeMessage(NotificationContext notificationContext) {
      NotificationInfo notificationInfo = notificationContext.getNotificationInfo();
      String pluginId = notificationInfo.getKey().getId();
      HTMLEntityEncoder encoder = HTMLEntityEncoder.getInstance();
      String processTitle = notificationInfo.getValueOwnerParameter(NotificationArguments.REQUEST_PROCESS.getKey());
      String requestTitle = notificationInfo.getValueOwnerParameter(NotificationArguments.REQUEST_TITLE.getKey());
      String commentAuthor = notificationInfo.getValueOwnerParameter(NotificationArguments.REQUEST_COMMENT_AUTHOR.getKey());
      String comment = notificationInfo.getValueOwnerParameter(NotificationArguments.REQUEST_COMMENT.getKey());
      String requestCommentUrl = notificationInfo.getValueOwnerParameter(NotificationArguments.REQUEST_COMMENT_URL.getKey());
      String language = getLanguage(notificationInfo);
      TemplateContext templateContext = TemplateContext.newChannelInstance(getChannelKey(), pluginId, language);
      String commentText = NotificationUtils.formatMention(comment);

      templateContext.put("PROCESS_TITLE", encoder.encode(processTitle));
      templateContext.put("REQUEST_TITLE", encoder.encode(requestTitle));
      templateContext.put("REQUEST_COMMENT_AUTHOR", encoder.encode(commentAuthor));
      templateContext.put("REQUEST_COMMENT_URL", encoder.encode(requestCommentUrl));
      templateContext.put("REQUEST_COMMENT", commentText);
      buildCommonTemplateParams(templateContext, notificationInfo, language, commentAuthor, encoder);

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
  
  private void buildCommonTemplateParams(TemplateContext templateContext,
                                         NotificationInfo notificationInfo,
                                         String language,
                                         String user,
                                         HTMLEntityEncoder encoder) {
    Profile userProfile = NotificationUtils.getUserProfile(user);
    templateContext.put("USER", encoder.encode(userProfile.getFullName()));
    templateContext.put("PROFILE_URL", encoder.encode(userProfile.getUrl()));
    templateContext.put("AVATAR", encoder.encode(LinkProviderUtils.getUserAvatarUrl(userProfile)));

    Calendar lastModified = Calendar.getInstance();
    lastModified.setTimeInMillis(notificationInfo.getLastModifiedDate());
    templateContext.put("LAST_UPDATED_TIME",
                        TimeConvertUtils.convertXTimeAgoByTimeServer(lastModified.getTime(),
                                                                     "EE, dd yyyy",
                                                                     new Locale(language),
                                                                     TimeConvertUtils.YEAR));
    boolean isRead = Boolean.parseBoolean(notificationInfo.getValueOwnerParameter(NotificationMessageUtils.READ_PORPERTY.getKey()));
    templateContext.put("READ", isRead ? "read" : "unread");
    templateContext.put("NOTIFICATION_ID", notificationInfo.getId());

    Identity receiver = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, notificationInfo.getTo());
    templateContext.put("FIRST_NAME", encoder.encode(receiver.getProfile().getProperty(Profile.FIRST_NAME).toString()));
    // Footer
    templateContext.put("FOOTER_LINK", LinkProviderUtils.getRedirectUrl("notification_settings", receiver.getRemoteId()));
  }
}
