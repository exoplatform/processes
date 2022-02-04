package org.exoplatform.processes.Utils;

import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;

public class Utils {

  private static final Log              LOG                         = ExoLogger.getLogger(Utils.class);



  public static String getUserNameByIdentityId(IdentityManager identityManager, long identityId) {
    Identity identity = identityManager.getIdentity(String.valueOf(identityId));
    return identity!=null ? identity.getRemoteId() : "";        
  }


}
