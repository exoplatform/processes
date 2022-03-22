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
import org.exoplatform.processes.notification.utils.NotificationArguments;
import org.exoplatform.processes.notification.utils.NotificationUtils;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.webui.utils.TimeConvertUtils;

import java.io.Writer;
import java.util.Calendar;
import java.util.Locale;

@TemplateConfigs(templates = {
    @TemplateConfig(pluginId = CreateRequestPlugin.ID, template = "war:/notification/templates/web/CreateRequestPlugin.gtmpl"),
    @TemplateConfig(pluginId = CancelRequestPlugin.ID, template = "war:/notification/templates/web/CancelRequestPlugin.gtmpl"), })
public class WebTemplateProvider extends TemplateProvider {

  public WebTemplateProvider(InitParams initParams) {
    super(initParams);
    this.templateBuilders.put(PluginKey.key(CreateRequestPlugin.ID), new TemplateBuilder());
    this.templateBuilders.put(PluginKey.key(CancelRequestPlugin.ID), new TemplateBuilder());

  }

  private class TemplateBuilder extends AbstractTemplateBuilder {

    @Override
    protected MessageInfo makeMessage(NotificationContext notificationContext) {
      NotificationInfo notificationInfo = notificationContext.getNotificationInfo();
      String pluginId = notificationInfo.getKey().getId();
      String requester = notificationInfo.getValueOwnerParameter(NotificationArguments.REQUEST_CREATOR.getKey());
      String processUrl = notificationInfo.getValueOwnerParameter(NotificationArguments.PROCESS_URL.getKey());
      String requestUrl = notificationInfo.getValueOwnerParameter(NotificationArguments.REQUEST_URL.getKey());
      Profile userProfile = NotificationUtils.getUserProfile(requester);
      String language = getLanguage(notificationInfo);
      TemplateContext templateContext = TemplateContext.newChannelInstance(getChannelKey(), pluginId, language);
      templateContext.put("USER", userProfile.getFullName());
      templateContext.put("PROFILE_URL", userProfile.getUrl());
      if (pluginId.equals(CreateRequestPlugin.ID)) {
        String processTitle = notificationInfo.getValueOwnerParameter(NotificationArguments.REQUEST_PROCESS.getKey());
        templateContext.put("PROCESS_TITLE", processTitle);
      }
      templateContext.put("PROCESS_URL", processUrl);
      templateContext.put("REQUEST_URL", requestUrl);
      templateContext.put("AVATAR", userProfile.getAvatarUrl());
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
