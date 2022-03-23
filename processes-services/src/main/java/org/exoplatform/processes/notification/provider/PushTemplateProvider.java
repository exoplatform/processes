package org.exoplatform.processes.notification.provider;

import org.exoplatform.commons.api.notification.NotificationContext;
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

import java.io.Writer;

@TemplateConfigs(templates = {
    @TemplateConfig(pluginId = CreateRequestPlugin.ID, template = "war:/notification/templates/push/CreateRequestPlugin.gtmpl"),
    @TemplateConfig(pluginId = CancelRequestPlugin.ID, template = "war:/notification/templates/push/CancelRequestPlugin.gtmpl"),
    @TemplateConfig(pluginId = RequestCommentPlugin.ID, template = "war:/notification/templates/push/RequestCommentPlugin.gtmpl"),})
public class PushTemplateProvider extends TemplateProvider {
  public PushTemplateProvider(InitParams initParams) {
    super(initParams);
    this.templateBuilders.put(PluginKey.key(CreateRequestPlugin.ID), new TemplateBuilder());
    this.templateBuilders.put(PluginKey.key(CancelRequestPlugin.ID), new TemplateBuilder());
    this.templateBuilders.put(PluginKey.key(RequestCommentPlugin.ID), new TemplateBuilder());
  }

  private class TemplateBuilder extends AbstractTemplateBuilder {

    @Override
    protected MessageInfo makeMessage(NotificationContext notificationContext) {
      NotificationInfo notificationInfo = notificationContext.getNotificationInfo();
      String pluginId = notificationInfo.getKey().getId();
      String language = getLanguage(notificationInfo);
      TemplateContext templateContext = TemplateContext.newChannelInstance(getChannelKey(), pluginId, language);
      String requester = notificationInfo.getValueOwnerParameter(NotificationArguments.REQUEST_CREATOR.getKey());
      String processTitle = notificationInfo.getValueOwnerParameter(NotificationArguments.REQUEST_PROCESS.getKey());

      if (pluginId.equals(RequestCommentPlugin.ID)) {
        String requestTitle = notificationInfo.getValueOwnerParameter(NotificationArguments.REQUEST_TITLE.getKey());
        String commentAuthor = notificationInfo.getValueOwnerParameter(NotificationArguments.REQUEST_COMMENT_AUTHOR.getKey());
        Profile userProfile = NotificationUtils.getUserProfile(commentAuthor);
        templateContext.put("REQUEST_TITLE", requestTitle);
        templateContext.put("REQUEST_COMMENT_AUTHOR", commentAuthor);
        templateContext.put("USER", userProfile.getFullName());
      } else {
        Profile userProfile = NotificationUtils.getUserProfile(requester);
        templateContext.put("USER", userProfile.getFullName());
      }
      templateContext.put("PROCESS_TITLE", processTitle);
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
}
