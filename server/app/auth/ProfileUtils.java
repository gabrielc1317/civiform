package auth;

import auth.controllers.MissingOptionalException;
import com.google.common.base.Preconditions;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import javax.inject.Inject;
import models.ApiKeyModel;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.profile.BasicUserProfile;
import org.pac4j.core.profile.ProfileManager;
import org.pac4j.play.PlayWebContext;
import play.mvc.Http;

// NON_ABSTRACT_CLASS_ALLOWS_SUBCLASSING ProfileUtils

/** A utility class for CiviForm profile. */
public class ProfileUtils {
  private final SessionStore sessionStore;
  private final ProfileFactory profileFactory;

  @Inject
  public ProfileUtils(SessionStore sessionStore, ProfileFactory profileFactory) {
    this.sessionStore = Preconditions.checkNotNull(sessionStore);
    this.profileFactory = Preconditions.checkNotNull(profileFactory);
  }

  /**
   * Fetch the current profile, or throw an exception if none is present. Note that {@link
   * filters.CiviFormProfileFilter} should guarantee that a profile is present for user-facing
   * requests.
   *
   * @throws MissingOptionalException if we can't find the profile from the request
   */
  public CiviFormProfile currentUserProfile(Http.RequestHeader request) {
    return optionalCurrentUserProfile(request)
        .orElseThrow(() -> new MissingOptionalException(CiviFormProfile.class));
  }

  /** Fetch the current pac4j profile for the given request. */
  public Optional<CiviFormProfile> optionalCurrentUserProfile(Http.RequestHeader request) {
    PlayWebContext webContext = new PlayWebContext(request);
    return optionalCurrentUserProfile(webContext);
  }

  /** Fetch the current pac4j profile for the given web context. */
  public Optional<CiviFormProfile> optionalCurrentUserProfile(WebContext webContext) {
    ProfileManager profileManager = new ProfileManager(webContext, sessionStore);
    Optional<CiviFormProfileData> p = profileManager.getProfile(CiviFormProfileData.class);
    if (p.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(profileFactory.wrapProfileData(p.get()));
  }

  public Optional<String> currentApiKeyId(Http.RequestHeader request) {
    PlayWebContext webContext = new PlayWebContext(request);
    ProfileManager profileManager = new ProfileManager(webContext, sessionStore);
    Optional<BasicUserProfile> profileMaybe = profileManager.getProfile(BasicUserProfile.class);

    return profileMaybe.map(BasicUserProfile::getId);
  }

  public Optional<ApiKeyModel> currentApiKey(Http.RequestHeader request) {
    Optional<String> maybeApiKeyId = currentApiKeyId(request);

    return maybeApiKeyId.map(profileFactory::retrieveApiKey);
  }

  // A temporary placeholder email value, used while the user needs to verify their account.
  private static final String IDCS_PLACEHOLDER_EMAIL_LOWERCASE =
      "ITD_UCSS_UAT@seattle.gov".toLowerCase(Locale.ROOT);
  // Testing account to allow for manual verification of the check.
  private static final String IDCS_PLACEHOLDER_TEST_EMAIL_LOWERCASE =
      "CiviFormStagingTest@gmail.com".toLowerCase(Locale.ROOT);

  /** Return true if the account is not a fully usable account to the City of Seattle. */
  public boolean accountIsIdcsPlaceholder(CiviFormProfile profile) {
    Optional<String> userEmail = Optional.ofNullable(profile.getProfileData().getEmail());

    if (userEmail.isEmpty()) {
      return false;
    }

    String userEmailLowercase = userEmail.get().toLowerCase(Locale.ROOT);

    return IDCS_PLACEHOLDER_EMAIL_LOWERCASE.equals(userEmailLowercase)
        || IDCS_PLACEHOLDER_TEST_EMAIL_LOWERCASE.equals(userEmailLowercase);
  }

  /** Return true if the account referenced by the profile exists. */
  public CompletionStage<Boolean> validCiviFormProfile(CiviFormProfile profile) {
    return profile
        .getAccount()
        .thenApply(account -> true)
        .exceptionally(
            ex -> {
              Throwable cause = (ex instanceof CompletionException) ? ex.getCause() : ex;
              if (cause instanceof AccountNonexistentException) {
                return false;
              }
              throw new CompletionException(cause);
            });
  }

  /** Retrieves the applicant id from the user profile, if present. */
  public Optional<Long> getApplicantId(Http.Request request) {
    Optional<CiviFormProfile> profile = optionalCurrentUserProfile(request);
    if (profile.isEmpty()) {
      return Optional.empty();
    }

    CiviFormProfileData profileData = profile.get().getProfileData();
    return Optional.ofNullable(
        profileData.getAttribute(ProfileFactory.APPLICANT_ID_ATTRIBUTE_NAME, Long.class));
  }
}
