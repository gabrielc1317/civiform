package modules;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import javax.inject.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.api.db.evolutions.ApplicationEvolutions;
import services.settings.SettingsService;

/**
 * Migrate ADMIN_WRITEABLE server settings from the Play Config system to database-backed admin
 * settings. Runs each time the server is started.
 */
public class SettingsMigrationModule extends AbstractModule {
  private static final Logger logger = LoggerFactory.getLogger(SettingsMigrationModule.class);

  @Override
  protected void configure() {
    logger.trace("Module Started");
    bind(SettingsMigrator.class).asEagerSingleton();
  }

  /**
   * This class injects ApplicationEvolutions and checks the `upToDate` method to prevent this
   * module from running until after the evolutions are completed.
   *
   * <p>See <a href="https://github.com/civiform/civiform/pull/8253">PR 8253</a> for more extensive
   * details.
   */
  public static final class SettingsMigrator {

    @Inject
    public SettingsMigrator(
        ApplicationEvolutions applicationEvolutions,
        Provider<SettingsService> settingsServiceProvider) {
      logger.trace("SettingsMigrator - Started");

      if (applicationEvolutions.upToDate()) {
        logger.trace("SettingsMigrator - Task Start");
        settingsServiceProvider.get().migrateConfigValuesToSettingsGroup();
        logger.trace("SettingsMigrator - Task End");
      } else {
        logger.trace("Evolutions Not Ready");
      }
    }
  }
}
