package org.exoplatform.processes.notification.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.MessageInfo;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.api.notification.model.PluginKey;
import org.exoplatform.commons.api.notification.service.template.TemplateContext;
import org.exoplatform.commons.api.notification.plugin.NotificationPluginUtils;
import org.exoplatform.commons.notification.template.TemplateUtils;
import org.exoplatform.commons.utils.TimeConvertUtils;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
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

/**
 * The mail templates include the shared footer
 * (war:/notification/templates/mail/NotificationFooter.gtmpl), which reads
 * $COMPANY_LINK and $FOOTER_LINK: every builder of the provider must bind them,
 * or the footer rendering fails with a MissingPropertyException (EXO-77511).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MailTemplateProviderTest {

  private static final String              BASE_URL    = "https://company.example.com";

  private static final String              FOOTER_URL  = "https://company.example.com/settings";

  private static final String              RECEIVER    = "receiver";

  private static final String              REQUESTER   = "requester";

  @Mock
  private InitParams                       initParams;

  @Mock
  private IdentityManager                  identityManager;

  @Mock
  private Identity                         receiverIdentity;

  @Mock
  private Profile                          profile;

  @Mock
  private NotificationContext              notificationContext;

  private MockedStatic<TemplateUtils>      templateUtils;

  private MockedStatic<NotificationPluginUtils> notificationPluginUtils;

  private MockedStatic<NotificationUtils>  notificationUtils;

  private MockedStatic<LinkProviderUtils>  linkProviderUtils;

  private MockedStatic<TimeConvertUtils>   timeConvertUtils;

  private final AtomicReference<TemplateContext> renderedContext = new AtomicReference<>();

  private MailTemplateProvider             mailTemplateProvider;

  @BeforeEach
  void setUp() {
    ValueParam channelParam = new ValueParam();
    channelParam.setName("channel-id");
    channelParam.setValue("MAIL_CHANNEL");
    when(initParams.getValueParam("channel-id")).thenReturn(channelParam);

    templateUtils = mockStatic(TemplateUtils.class);
    templateUtils.when(() -> TemplateUtils.processSubject(any())).thenReturn("subject");
    templateUtils.when(() -> TemplateUtils.processGroovy(any())).thenAnswer(invocation -> {
      renderedContext.set(invocation.getArgument(0));
      return "body";
    });

    notificationPluginUtils = mockStatic(NotificationPluginUtils.class);
    notificationPluginUtils.when(() -> NotificationPluginUtils.getLanguage(anyString())).thenReturn("en");

    notificationUtils = mockStatic(NotificationUtils.class);
    notificationUtils.when(() -> NotificationUtils.getUserProfile(anyString())).thenReturn(profile);
    notificationUtils.when(() -> NotificationUtils.formatMention(any())).thenReturn("comment");

    linkProviderUtils = mockStatic(LinkProviderUtils.class);
    linkProviderUtils.when(LinkProviderUtils::getBaseUrl).thenReturn(BASE_URL);
    linkProviderUtils.when(() -> LinkProviderUtils.getUserAvatarUrl(any())).thenReturn("/avatar");
    linkProviderUtils.when(() -> LinkProviderUtils.getRedirectUrl("notification_settings", RECEIVER)).thenReturn(FOOTER_URL);

    timeConvertUtils = mockStatic(TimeConvertUtils.class);

    when(profile.getFullName()).thenReturn("Requester Name");
    when(profile.getUrl()).thenReturn("/profile/requester");
    when(profile.getProperty(Profile.FIRST_NAME)).thenReturn("Receiver");
    when(receiverIdentity.getProfile()).thenReturn(profile);
    when(receiverIdentity.getRemoteId()).thenReturn(RECEIVER);
    when(identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, RECEIVER)).thenReturn(receiverIdentity);

    mailTemplateProvider = new MailTemplateProvider(initParams, identityManager);
  }

  @AfterEach
  void tearDown() {
    templateUtils.close();
    notificationPluginUtils.close();
    notificationUtils.close();
    linkProviderUtils.close();
    timeConvertUtils.close();
  }

  @ParameterizedTest
  @ValueSource(strings = { CreateRequestPlugin.ID, CancelRequestPlugin.ID, RequestCommentPlugin.ID })
  void shouldBindTheFooterLinksOfTheSharedFooter(String pluginId) {
    NotificationInfo notification = new NotificationInfo();
    notification.key(pluginId);
    notification.setTo(RECEIVER);
    notification.setLastModifiedDate(System.currentTimeMillis());
    notification.with(NotificationArguments.REQUEST_CREATOR.getKey(), REQUESTER);
    notification.with(NotificationArguments.REQUEST_COMMENT_AUTHOR.getKey(), REQUESTER);
    notification.with(NotificationArguments.REQUEST_PROCESS.getKey(), "process");
    notification.with(NotificationArguments.REQUEST_TITLE.getKey(), "request");
    notification.with(NotificationArguments.REQUEST_DESCRIPTION.getKey(), "description");
    notification.with(NotificationArguments.PROCESS_URL.getKey(), "/process/1");
    notification.with(NotificationArguments.REQUEST_URL.getKey(), "/request/1");
    notification.with(NotificationArguments.REQUEST_COMMENT.getKey(), "comment");
    notification.with(NotificationArguments.REQUEST_COMMENT_URL.getKey(), "/request/1/comments");
    when(notificationContext.getNotificationInfo()).thenReturn(notification);

    MessageInfo messageInfo = mailTemplateProvider.getTemplateBuilder().get(PluginKey.key(pluginId)).buildMessage(notificationContext);

    assertNotNull(messageInfo);
    ArgumentCaptor<Exception> exceptions = ArgumentCaptor.forClass(Exception.class);
    verify(notificationContext, atLeastOnce()).setException(exceptions.capture());
    exceptions.getAllValues().forEach(e -> assertNull(e, () -> "the mail rendering failed: " + e));
    TemplateContext templateContext = renderedContext.get();
    assertNotNull(templateContext, "the mail body was not rendered");
    assertEquals(BASE_URL, templateContext.get("COMPANY_LINK"));
    assertEquals(FOOTER_URL, templateContext.get("FOOTER_LINK"));
  }
}
