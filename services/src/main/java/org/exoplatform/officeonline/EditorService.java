package org.exoplatform.officeonline;

import java.io.InputStream;
import java.security.Key;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.commons.io.input.AutoCloseInputStream;

import org.exoplatform.officeonline.exception.OfficeOnlineException;
import org.exoplatform.services.cache.CacheService;
import org.exoplatform.services.cms.documents.DocumentService;
import org.exoplatform.services.idgenerator.IDGeneratorService;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.security.Authenticator;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.IdentityRegistry;

// TODO: Auto-generated Javadoc
/**
 * The Class EditorService.
 */
public class EditorService extends AbstractOfficeOnlineService {

  /** The Constant TOKEN_DELIMITER. */
  protected static final String TOKEN_DELIMITER      = "+";

  /** The Constant TOKEN_DELIMITER_SPLIT. */
  protected static final String TOKEN_DELIMITE_SPLIT = "\\+";

  /** The Constant LOG. */
  protected static final Log    LOG                  = ExoLogger.getLogger(EditorService.class);

  /**
   * Instantiates a new editor service.
   *
   * @param sessionProviders the session providers
   * @param idGenerator the id generator
   * @param jcrService the jcr service
   * @param organization the organization
   * @param documentService the document service
   * @param authenticator the authenticator
   * @param identityRegistry the identity registry
   */
  public EditorService(SessionProviderService sessionProviders,
                       IDGeneratorService idGenerator,
                       RepositoryService jcrService,
                       OrganizationService organization,
                       DocumentService documentService,
                       Authenticator authenticator,
                       IdentityRegistry identityRegistry,
                       CacheService cacheService) {
    super(sessionProviders,
          idGenerator,
          jcrService,
          organization,
          documentService,
          authenticator,
          identityRegistry,
          cacheService);
  }

  /**
   * Creates the editor config.
   *
   * @param userSchema the user schema
   * @param userHost the user host
   * @param userPort the user port
   * @param userId the user id
   * @param workspace the workspace
   * @param fileId the file id
   * @return the editor config
   * @throws RepositoryException the repository exception
   * @throws OfficeOnlineException the office online exception
   */
  public EditorConfig createEditorConfig(String userSchema,
                                         String userHost,
                                         int userPort,
                                         String userId,
                                         String workspace,
                                         String fileId) throws RepositoryException, OfficeOnlineException {
    EditorConfig config = null;
    // remember real context state and session provider to restore them at the end
    ConversationState contextState = ConversationState.getCurrent();
    SessionProvider contextProvider = sessionProviders.getSessionProvider(null);
    try {
      if (!setUserConvoState(userId)) {
        LOG.error("Couldn't set user conversation state. UserId: {}", userId);
        throw new OfficeOnlineException("Cannot set conversation state " + userId);
      }

      Node document = nodeByUUID(workspace, fileId);
      List<Permissions> permissions = new ArrayList<>();

      if (document != null) {
        if (canEditDocument(document)) {
          permissions.add(Permissions.USER_CAN_WRITE);
          permissions.add(Permissions.USER_CAN_RENAME);
        } else {
          permissions.add(Permissions.READ_ONLY);
        }
      }

      config = new EditorConfig(userId, fileId, workspace, permissions);
      String accessToken = generateAccessToken(config);
      config.setAccessToken(accessToken);
    } finally {
      restoreConvoState(contextState, contextProvider);
    }
    return config;
  }

  /**
   * Generate access token.
   *
   * @param config the config
   * @return the string
   */
  protected String generateAccessToken(EditorConfig config) {
    try {
      String keyStr = activeCache.get(SECRET_KEY);
      byte[] decodedKey = Base64.getDecoder().decode(keyStr);
      SecretKey key = new SecretKeySpec(decodedKey, 0, decodedKey.length, ALGORITHM);
      Cipher c = Cipher.getInstance(ALGORITHM);
      c.init(Cipher.ENCRYPT_MODE, key);
      StringBuilder builder = new StringBuilder().append(config.getWorkspace())
                                                 .append(TOKEN_DELIMITER)
                                                 .append(config.getUserId())
                                                 .append(TOKEN_DELIMITER)
                                                 .append(config.getFileId());
      config.getPermissions().forEach(permission -> {
        builder.append(TOKEN_DELIMITER).append(permission.getShortName());
      });
      byte[] encrypted = c.doFinal(builder.toString().getBytes());
      return new String(Base64.getUrlEncoder().encode(encrypted));
    } catch (Exception e) {
      LOG.error("Cannot generate access token. {}", e.getMessage());
    }
    return null;
  }

  /**
   * Builds the editor config.
   *
   * @param accessToken the access token
   * @return the editor config
   * @throws OfficeOnlineException the office online exception
   */
  protected EditorConfig buildEditorConfig(String accessToken) throws OfficeOnlineException {
    String decryptedToken = "";
    try {
      // TODO: store Key in cache instead of String representation of it
      String keyStr = activeCache.get(SECRET_KEY);
      byte[] decodedKey = Base64.getDecoder().decode(keyStr);
      SecretKey key = new SecretKeySpec(decodedKey, 0, decodedKey.length, ALGORITHM);
      Cipher c = Cipher.getInstance(ALGORITHM);
      c.init(Cipher.DECRYPT_MODE, key);
      byte[] decoded = Base64.getUrlDecoder().decode(accessToken.getBytes());
      decryptedToken = new String(c.doFinal(decoded));
    } catch (Exception e) {
      LOG.error("Error occured while decoding/decrypting accessToken. {}", e.getMessage());
      throw new OfficeOnlineException("Cannot decode/decrypt accessToken");
    }

    List<Permissions> permissions = new ArrayList<>();
    List<String> values = Arrays.asList(decryptedToken.split(TOKEN_DELIMITE_SPLIT));
    if (values.size() > 2) {
      String workspace = values.get(0);
      String userId = values.get(1);
      String fileId = values.get(2);
      values.stream().skip(3).forEach(value -> permissions.add(Permissions.fromShortName(value)));
      return new EditorConfig(userId, fileId, workspace, permissions, accessToken);
    } else {
      throw new OfficeOnlineException("Decrypted token doesn't contain all required parameters");
    }
  }

  /**
   * Gets the content.
   *
   * @param userId the user id
   * @param fileId the file id
   * @param accessToken the access token
   * @return the content
   * @throws OfficeOnlineException the office online exception
   */
  public DocumentContent getContent(String accessToken) throws OfficeOnlineException {
    EditorConfig config = buildEditorConfig(accessToken);
    ConversationState contextState = ConversationState.getCurrent();
    SessionProvider contextProvider = sessionProviders.getSessionProvider(null);
    try {
      // We all the job under actual (requester) user here
      if (!setUserConvoState(config.getUserId())) {
        LOG.error("Couldn't set user conversation state. UserId: {}", config.getUserId());
        throw new OfficeOnlineException("Cannot set conversation state " + config.getUserId());
      }
      // work in user session
      Node node = nodeByUUID(config.getFileId(), config.getWorkspace());
      if (node == null) {
        throw new OfficeOnlineException("File not found. fileId: " + config.getFileId());
      }
      Node content = nodeContent(node);

      final String mimeType = content.getProperty("jcr:mimeType").getString();
      // data stream will be closed when EoF will be reached
      final InputStream data = new AutoCloseInputStream(content.getProperty("jcr:data").getStream());
      return new DocumentContent() {
        @Override
        public String getType() {
          return mimeType;
        }

        @Override
        public InputStream getData() {
          return data;
        }
      };
    } catch (RepositoryException e) {
      LOG.error("Cannot get content of node. FileId: " + config.getFileId(), e.getMessage());
      throw new OfficeOnlineException("Cannot get file content");
    } finally {
      restoreConvoState(contextState, contextProvider);
    }

  }

  /**
   * Start.
   */
  @Override
  public void start() {
    LOG.debug("Editor Service started");

    EditorConfig config = new EditorConfig("vlad",
                                           "93268635624323427",
                                           "collaboration",
                                           Arrays.asList(Permissions.USER_CAN_WRITE, Permissions.USER_CAN_RENAME));
    String accessToken = generateAccessToken(config);
    LOG.debug("Access token #1: " + accessToken);

    EditorConfig config2 = new EditorConfig("peter", "09372697", "private", new ArrayList<Permissions>());
    String accessToken2 = generateAccessToken(config2);

    LOG.debug("Access token #2: " + accessToken2);
    try {
      EditorConfig decrypted1 = buildEditorConfig(accessToken);
      EditorConfig decrypted2 = buildEditorConfig(accessToken2);

      LOG.debug("DECRYPTED 1: " + decrypted1.getWorkspace() + " " + decrypted1.getUserId() + " " + decrypted1.getFileId());
      decrypted1.getPermissions().forEach(LOG::debug);

      LOG.debug("DECRYPTED 2: " + decrypted2.getWorkspace() + " " + decrypted2.getUserId() + " " + decrypted2.getFileId());
      decrypted2.getPermissions().forEach(LOG::debug);
    } catch (OfficeOnlineException e) {
      LOG.error(e);
    }

  }

  /**
   * Stop.
   */
  @Override
  public void stop() {
    // TODO Auto-generated method stub
  }

}
