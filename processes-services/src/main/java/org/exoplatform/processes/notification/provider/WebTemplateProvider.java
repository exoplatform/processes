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
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.processes.notification.plugin.CancelRequestPlugin;
import org.exoplatform.processes.notification.plugin.CreateRequestPlugin;
import org.exoplatform.processes.notification.plugin.RequestCommentPlugin;
import org.exoplatform.processes.notification.utils.NotificationArguments;
import org.exoplatform.processes.notification.utils.NotificationUtils;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.webui.utils.TimeConvertUtils;

import java.io.Writer;
import java.util.Calendar;
import java.util.Locale;

@TemplateConfigs(templates = {
    @TemplateConfig(pluginId = CreateRequestPlugin.ID, template = "war:/notification/templates/web/CreateRequestPlugin.gtmpl"),
    @TemplateConfig(pluginId = CancelRequestPlugin.ID, template = "war:/notification/templates/web/CancelRequestPlugin.gtmpl"),
    @TemplateConfig(pluginId = RequestCommentPlugin.ID, template = "war:/notification/templates/web/RequestCommentPlugin.gtmpl"),})
public class WebTemplateProvider extends TemplateProvider {

  public WebTemplateProvider(InitParams initParams) {
    super(initParams);
    this.templateBuilders.put(PluginKey.key(CreateRequestPlugin.ID), new TemplateBuilder());
    this.templateBuilders.put(PluginKey.key(CancelRequestPlugin.ID), new TemplateBuilder());
    this.templateBuilders.put(PluginKey.key(RequestCommentPlugin.ID), new RequestCommentTemplateBuilder());
  }

  private class TemplateBuilder extends AbstractTemplateBuilder {

    @Override
    protected MessageInfo makeMessage(NotificationContext notificationContext) {
      NotificationInfo notificationInfo = notificationContext.getNotificationInfo();
      String pluginId = notificationInfo.getKey().getId();
      String requester = notificationInfo.getValueOwnerParameter(NotificationArguments.REQUEST_CREATOR.getKey());
      String processUrl = notificationInfo.getValueOwnerParameter(NotificationArguments.PROCESS_URL.getKey());
      String requestUrl = notificationInfo.getValueOwnerParameter(NotificationArguments.REQUEST_URL.getKey());
      String language = getLanguage(notificationInfo);
      TemplateContext templateContext = TemplateContext.newChannelInstance(getChannelKey(), pluginId, language);
    
      if (pluginId.equals(CreateRequestPlugin.ID)) {
        String processTitle = notificationInfo.getValueOwnerParameter(NotificationArguments.REQUEST_PROCESS.getKey());
        templateContext.put("PROCESS_TITLE", processTitle);
      }
      
      templateContext.put("PROCESS_URL", processUrl);
      templateContext.put("REQUEST_URL", requestUrl);
      buildCommonTemplateParams(templateContext, notificationInfo, language, requester);

      String body = TemplateUtils.processGroovy(templateContext);
      notificationContext.setException(templateContext.getException());
      MessageInfo messageInfo = new MessageInfo();
      return messageInfo.body(body).end();
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
      String processTitle = notificationInfo.getValueOwnerParameter(NotificationArguments.REQUEST_PROCESS.getKey());
      String requestTitle = notificationInfo.getValueOwnerParameter(NotificationArguments.REQUEST_TITLE.getKey());
      String commentAuthor = notificationInfo.getValueOwnerParameter(NotificationArguments.REQUEST_COMMENT_AUTHOR.getKey());
      String comment = notificationInfo.getValueOwnerParameter(NotificationArguments.REQUEST_COMMENT.getKey());
      String requestCommentUrl = notificationInfo.getValueOwnerParameter(NotificationArguments.REQUEST_COMMENT_URL.getKey());
      String language = getLanguage(notificationInfo);
      TemplateContext templateContext = TemplateContext.newChannelInstance(getChannelKey(), pluginId, language);
      String commentText = NotificationUtils.formatMention(comment);

      templateContext.put("PROCESS_TITLE", processTitle);
      templateContext.put("REQUEST_TITLE", requestTitle);
      templateContext.put("REQUEST_COMMENT_AUTHOR", commentAuthor);
      templateContext.put("REQUEST_COMMENT_URL", requestCommentUrl);
      templateContext.put("REQUEST_COMMENT", commentText);
      buildCommonTemplateParams(templateContext, notificationInfo, language, commentAuthor);

      String body = TemplateUtils.processGroovy(templateContext);
      notificationContext.setException(templateContext.getException());
      MessageInfo messageInfo = new MessageInfo();
      return messageInfo.body(body).end();
    }

    @Override
    protected boolean makeDigest(NotificationContext notificationContext, Writer writer) {
      return false;
    }
  }
  
  private void buildCommonTemplateParams(TemplateContext templateContext,
                                         NotificationInfo notificationInfo,
                                         String language,
                                         String user) {
    Profile userProfile = NotificationUtils.getUserProfile(user);
    templateContext.put("USER", userProfile.getFullName());
    templateContext.put("PROFILE_URL", userProfile.getUrl());
    templateContext.put("AVATAR", userProfile.getAvatarUrl());
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
  }

}
