package services.settings;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.util.Optional;
import org.junit.Test;

public class SettingsManifestTest {

  private static SettingDescription BOOL_VARIABLE =
      SettingDescription.create(
          "BOOL_VARIABLE",
          "Fake subsection variable for testing",
          SettingType.BOOLEAN,
          SettingMode.ADMIN_READABLE);
  private static SettingDescription STRING_VARIABLE =
      SettingDescription.create(
          "STRING_VARIABLE",
          "Fake subsection variable for testing",
          SettingType.STRING,
          SettingMode.ADMIN_READABLE);
  private static SettingDescription ENUM_VARIABLE =
      SettingDescription.create(
          "ENUM_VARIABLE",
          "Fake subsection variable for testing",
          SettingType.ENUM,
          SettingMode.ADMIN_READABLE,
          ImmutableList.of("foo", "bar", "baz"));
  private static SettingDescription LIST_OF_STRINGS_VARIABLE =
      SettingDescription.create(
          "LIST_OF_STRINGS_VARIABLE",
          "Fake subsection variable for testing",
          SettingType.LIST_OF_STRINGS,
          SettingMode.ADMIN_READABLE);
  private static SettingDescription INT_VARIABLE =
      SettingDescription.create(
          "INT_VARIABLE",
          "Fake subsection variable for testing",
          SettingType.INT,
          SettingMode.ADMIN_READABLE);
  private static SettingDescription UNSET_STRING_VARIABLE =
      SettingDescription.create(
          "UNSET_STRING_VARIABLE",
          "Fake subsection variable for testing",
          SettingType.INT,
          SettingMode.ADMIN_READABLE);

  private static Config CONFIG =
      ConfigFactory.parseMap(
          ImmutableMap.of(
              "bool_variable",
              true,
              "string_variable",
              "my-var",
              "list_of_strings_variable",
              ImmutableList.of("one", "two", "three"),
              "int_variable",
              11,
              "enum_variable",
              "foo"));

  private static ImmutableMap<String, SettingsSection> SECTIONS =
      ImmutableMap.of(
          "TEST_SECTION",
          SettingsSection.create(
              "Test Section",
              "Fake section for testing.",
              ImmutableList.of(
                  SettingsSection.create(
                      "Test Subsection",
                      "Fake subsection for testing",
                      ImmutableList.of(),
                      ImmutableList.of(BOOL_VARIABLE))),
              ImmutableList.of(
                  SettingDescription.create(
                      "STRING_VARIABLE",
                      "Fake string variable for testing",
                      SettingType.STRING,
                      SettingMode.ADMIN_READABLE))));
  private SettingsManifest testManifest = new SettingsManifest(SECTIONS, CONFIG);

  @Test
  public void gettingSections() {
    assertThat(testManifest.getSections()).isEqualTo(SECTIONS);
  }

  @Test
  public void getSettingDisplayValue() {
    assertThat(testManifest.getSettingDisplayValue(STRING_VARIABLE))
        .isEqualTo(Optional.of("my-var"));
    assertThat(testManifest.getSettingDisplayValue(INT_VARIABLE)).isEqualTo(Optional.of("11"));
    assertThat(testManifest.getSettingDisplayValue(LIST_OF_STRINGS_VARIABLE))
        .isEqualTo(Optional.of("one, two, three"));
    assertThat(testManifest.getSettingDisplayValue(BOOL_VARIABLE)).isEqualTo(Optional.of("TRUE"));
    assertThat(testManifest.getSettingDisplayValue(ENUM_VARIABLE)).isEqualTo(Optional.of("foo"));
    assertThat(testManifest.getSettingDisplayValue(UNSET_STRING_VARIABLE))
        .isEqualTo(Optional.empty());
  }
}
