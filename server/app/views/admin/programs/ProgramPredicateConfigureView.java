package views.admin.programs;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static j2html.TagCreator.div;
import static j2html.TagCreator.each;
import static j2html.TagCreator.form;
import static j2html.TagCreator.h1;
import static j2html.TagCreator.iff;
import static j2html.TagCreator.input;
import static j2html.TagCreator.option;
import static j2html.TagCreator.select;
import static j2html.TagCreator.text;
import static play.mvc.Http.HttpVerbs.POST;
import static views.ViewUtils.ProgramDisplayType.DRAFT;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import controllers.admin.routes;
import j2html.tags.specialized.ATag;
import j2html.tags.specialized.DivTag;
import j2html.tags.specialized.FormTag;
import j2html.tags.specialized.LabelTag;
import j2html.tags.specialized.OptionTag;
import j2html.tags.specialized.SelectTag;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import javax.inject.Inject;
import play.mvc.Http;
import play.twirl.api.Content;
import services.applicant.question.Scalar;
import services.geo.esri.EsriServiceAreaValidationConfig;
import services.geo.esri.EsriServiceAreaValidationOption;
import services.program.BlockDefinition;
import services.program.EligibilityDefinition;
import services.program.ProgramDefinition;
import services.program.predicate.AndNode;
import services.program.predicate.LeafAddressServiceAreaExpressionNode;
import services.program.predicate.LeafExpressionNode;
import services.program.predicate.LeafOperationExpressionNode;
import services.program.predicate.Operator;
import services.program.predicate.PredicateAction;
import services.program.predicate.PredicateDefinition;
import services.program.predicate.PredicateExpressionNode;
import services.program.predicate.PredicateExpressionNodeType;
import services.program.predicate.PredicateValue;
import services.question.QuestionOption;
import services.question.exceptions.InvalidQuestionTypeException;
import services.question.exceptions.UnsupportedQuestionTypeException;
import services.question.types.MultiOptionQuestionDefinition;
import services.question.types.QuestionDefinition;
import services.question.types.QuestionType;
import services.settings.SettingsManifest;
import views.HtmlBundle;
import views.ViewUtils.ProgramDisplayType;
import views.admin.AdminLayout;
import views.admin.AdminLayoutFactory;
import views.components.FieldWithLabel;
import views.components.Icons;
import views.components.LinkElement;
import views.components.SelectWithLabel;
import views.components.TextFormatter;
import views.style.BaseStyles;
import views.style.ReferenceClasses;

/**
 * Admin UI for specifying scalars, operators, and values for {@link PredicateDefinition}s for both
 * eligibility and visibility predicates.
 *
 * <p>Scalars and operators are specified one per question with one or many values, all in a column.
 *
 * <p>If only one value is specified by the admin, a SINGLE_QUESTION (single leaf node) {@link
 * PredicateDefinition.PredicateFormat} will result.
 *
 * <p>Multiple values per question can be specified in equal number across all questions. Each set
 * of question values are grouped together to form an AND clause. Each group corresponds to an AND
 * node, with one leaf node per question. If multiple groups or questions are specified an
 * OR_OF_SINGLE_LAYER_ANDS {@link PredicateDefinition.PredicateFormat} will result.
 */
public final class ProgramPredicateConfigureView extends ProgramBaseView {

  private static final String COLUMN_WIDTH = "w-48";

  public enum Type {
    ELIGIBILITY,
    VISIBILITY
  }

  private final AdminLayout layout;
  private final EsriServiceAreaValidationConfig esriServiceAreaValidationConfig;

  @Inject
  public ProgramPredicateConfigureView(
      AdminLayoutFactory layoutFactory,
      EsriServiceAreaValidationConfig esriServiceAreaValidationConfig,
      SettingsManifest settingsManifest) {
    super(settingsManifest);
    this.layout = checkNotNull(layoutFactory).getLayout(AdminLayout.NavPage.PROGRAMS);
    this.esriServiceAreaValidationConfig = checkNotNull(esriServiceAreaValidationConfig);
  }

  public Content renderNewVisibility(
      Http.Request request,
      ProgramDefinition programDefinition,
      BlockDefinition blockDefinition,
      ImmutableList<QuestionDefinition> questionDefinitions) {
    return render(
        request,
        programDefinition,
        blockDefinition,
        questionDefinitions,
        Type.VISIBILITY,
        /* forceNew= */ true);
  }

  public Content renderExistingVisibility(
      Http.Request request,
      ProgramDefinition programDefinition,
      BlockDefinition blockDefinition,
      ImmutableList<QuestionDefinition> questionDefinitions) {
    return render(
        request,
        programDefinition,
        blockDefinition,
        questionDefinitions,
        Type.VISIBILITY,
        /* forceNew= */ false);
  }

  public Content renderNewEligibility(
      Http.Request request,
      ProgramDefinition programDefinition,
      BlockDefinition blockDefinition,
      ImmutableList<QuestionDefinition> questionDefinitions) {
    return render(
        request,
        programDefinition,
        blockDefinition,
        questionDefinitions,
        Type.ELIGIBILITY,
        /* forceNew= */ true);
  }

  public Content renderExistingEligibility(
      Http.Request request,
      ProgramDefinition programDefinition,
      BlockDefinition blockDefinition,
      ImmutableList<QuestionDefinition> questionDefinitions) {
    return render(
        request,
        programDefinition,
        blockDefinition,
        questionDefinitions,
        Type.ELIGIBILITY,
        /* forceNew= */ false);
  }

  public Content render(
      Http.Request request,
      ProgramDefinition programDefinition,
      BlockDefinition blockDefinition,
      ImmutableList<QuestionDefinition> questionDefinitions,
      Type type,
      boolean forceNew) {

    // The order of the question definitions in the list determines the column
    // order in the UI. Questions are ordered by admin name to ensure stable
    // ordering.
    questionDefinitions =
        questionDefinitions.stream()
            .sorted(Comparator.comparing(QuestionDefinition::getName))
            .collect(toImmutableList());

    final String editPredicateUrl;
    final String typeDisplayName;
    final String formActionUrl;
    final Optional<PredicateDefinition> existingPredicate;

    switch (type) {
      case ELIGIBILITY:
        {
          typeDisplayName = "eligibility";
          existingPredicate =
              forceNew
                  ? Optional.empty()
                  : blockDefinition.eligibilityDefinition().map(EligibilityDefinition::predicate);
          formActionUrl =
              routes.AdminProgramBlockPredicatesController.updateEligibility(
                      programDefinition.id(), blockDefinition.id())
                  .url();
          editPredicateUrl =
              routes.AdminProgramBlockPredicatesController.editEligibility(
                      programDefinition.id(), blockDefinition.id())
                  .url();
          break;
        }
      case VISIBILITY:
        {
          typeDisplayName = "visibility";
          existingPredicate = forceNew ? Optional.empty() : blockDefinition.visibilityPredicate();
          formActionUrl =
              routes.AdminProgramBlockPredicatesController.updateVisibility(
                      programDefinition.id(), blockDefinition.id())
                  .url();
          editPredicateUrl =
              routes.AdminProgramBlockPredicatesController.editVisibility(
                      programDefinition.id(), blockDefinition.id())
                  .url();
          break;
        }
      default:
        {
          throw new IllegalArgumentException(
              String.format("Unknown predicate view type: %s", type));
        }
    }

    DivTag content =
        div(
                renderBackLink(editPredicateUrl, typeDisplayName, blockDefinition),
                div()
                    .with(
                        h1(String.format("Configure %s conditions", typeDisplayName))
                            .withClasses("font-bold", "text-xl", "mb-2"))
                    .with(each(questionDefinitions, this::renderQuestionCard)),
                renderPredicateConfigurator(
                    request, formActionUrl, questionDefinitions, existingPredicate, type))
            .withClasses("mx-6", "max-w-6xl", "mb-12", "my-10", "flex", "flex-col", "gap-6");

    HtmlBundle htmlBundle =
        layout
            .getBundle(request)
            .setTitle(String.format("Configure %s predicate", typeDisplayName))
            .addMainContent(
                renderProgramInfoHeader(
                    programDefinition,
                    ImmutableList.of(ProgramHeaderButton.EDIT_PROGRAM_DETAILS),
                    request),
                content);
    return layout.renderCentered(htmlBundle);
  }

  private ATag renderBackLink(
      String editPredicateUrl, String typeDisplayName, BlockDefinition blockDefinition) {
    return new LinkElement()
        .setHref(editPredicateUrl)
        .setIcon(Icons.ARROW_LEFT, LinkElement.IconPosition.START)
        .setText(
            String.format("Back to %s conditions for %s", typeDisplayName, blockDefinition.name()))
        .asAnchorText();
  }

  private FormTag renderPredicateConfigurator(
      Http.Request request,
      String formAction,
      ImmutableList<QuestionDefinition> questionDefinitions,
      Optional<PredicateDefinition> maybeExistingPredicate,
      Type type) {
    FormTag formTag =
        form(
                makeCsrfTokenInputTag(request),
                type.equals(Type.ELIGIBILITY)
                    ? renderConfiguratorEligibilityHeader()
                    : renderConfiguratorVisibilityHeader(maybeExistingPredicate),
                renderQuestionHeaders(questionDefinitions, maybeExistingPredicate))
            .withAction(formAction)
            .withMethod(POST)
            .withClasses(
                "overflow-x-scroll",
                "p-8",
                "bg-gray-100",
                "predicate-config-form",
                ReferenceClasses.PREDICATE_OPTIONS);

    DivTag valueRowContainer = div().withId("predicate-config-value-row-container");

    DivTag helpTextRow = div().withClasses("flex", "predicate-help-text-row");
    valueRowContainer.with(helpTextRow);
    DivTag spacerText = div().withClasses("w-16", "shrink-0");

    int columnNumber = 0;
    for (QuestionDefinition questionDefinition : questionDefinitions) {
      helpTextRow
          .condWith(columnNumber++ != 0, spacerText)
          .with(
              div()
                  .withClasses("w-48", "shrink-0")
                  .with(
                      div()
                          .withClasses(
                              ReferenceClasses.PREDICATE_VALUE_COMMA_HELP_TEXT,
                              "hidden",
                              "text-xs",
                              "pb-4",
                              BaseStyles.FORM_LABEL_TEXT_COLOR)
                          .withText(
                              "Enter a list of comma-separated values. For example, \"v1,v2,v3\".")
                          .withData("question-id", String.valueOf(questionDefinition.getId()))));
    }

    if (maybeExistingPredicate.isPresent()) {
      PredicateDefinition existingPredicate = maybeExistingPredicate.get();

      AtomicInteger groupCount = new AtomicInteger(0);
      getExistingAndNodes(existingPredicate).stream()
          .map(PredicateExpressionNode::getAndNode)
          .forEach(
              andNode ->
                  valueRowContainer.with(
                      renderValueRow(
                          questionDefinitions,
                          groupCount.incrementAndGet(),
                          Optional.of(andNode))));
    } else {
      valueRowContainer.with(
          renderValueRow(questionDefinitions, 1, /* maybeAndNode= */ Optional.empty()));
    }

    formTag.with(valueRowContainer);
    formTag.with(div(makeSvgTextButton("Add values", Icons.ADD).withId("predicate-add-value-row")));
    formTag.with(
        submitButton("Save condition").withId("predicate-submit-button").withClasses("my-4"));

    return formTag;
  }

  private static DivTag renderConfiguratorEligibilityHeader() {
    return div(
            text("Applicant is eligible when:"),
            input()
                .withType("hidden")
                .withName("predicateAction")
                .withValue(PredicateAction.ELIGIBLE_BLOCK.name()))
        .withClasses("font-bold");
  }

  private static DivTag renderConfiguratorVisibilityHeader(
      Optional<PredicateDefinition> maybeExistingPredicate) {
    DivTag container =
        div("Screen is ").withClasses("font-bold", ReferenceClasses.PREDICATE_ACTION);
    SelectTag selectTag = select().withName("predicateAction");

    ImmutableList.of(PredicateAction.HIDE_BLOCK, PredicateAction.SHOW_BLOCK)
        .forEach(
            action ->
                selectTag.with(
                    option(action.toDisplayString())
                        .withClasses(
                            ReferenceClasses.MULTI_OPTION_QUESTION_OPTION,
                            ReferenceClasses.MULTI_OPTION_VALUE)
                        .withCondSelected(
                            maybeExistingPredicate
                                .map(PredicateDefinition::action)
                                .map(existingAction -> existingAction.equals(action))
                                .orElse(false))
                        .withValue(action.name())));

    container.with(selectTag);

    return container;
  }

  /**
   * Presents the existing predicate as a list of {@link AndNode}'s regardless of actual AST
   * structure. This is done to make rendering the tabular view easier.
   */
  private static ImmutableList<PredicateExpressionNode> getExistingAndNodes(
      PredicateDefinition existingPredicate) {
    PredicateDefinition.PredicateFormat format = existingPredicate.predicateFormat();
    return switch (format) {
      case SINGLE_QUESTION ->
          ImmutableList.of(
              PredicateExpressionNode.create(
                  AndNode.create(ImmutableList.of(existingPredicate.rootNode()))));
      case OR_OF_SINGLE_LAYER_ANDS -> existingPredicate.rootNode().getOrNode().children();
      default ->
          throw new IllegalArgumentException(
              String.format("Unrecognized predicate format: %s", format));
    };
  }

  private static ImmutableMap<Long, LeafExpressionNode> getOneRowOfLeafNodes(
      PredicateDefinition existingPredicate) {
    return getExistingAndNodes(existingPredicate).get(0).getAndNode().children().stream()
        .map(PredicateExpressionNode::getLeafNode)
        .collect(ImmutableMap.toImmutableMap(LeafExpressionNode::questionId, Function.identity()));
  }

  private DivTag renderQuestionHeaders(
      ImmutableList<QuestionDefinition> questionDefinitions,
      Optional<PredicateDefinition> maybeExistingPredicate) {
    DivTag container = div().withClasses("flex", "py-4");

    ImmutableMap<Long, LeafExpressionNode> questionIdLeafNodeMap =
        maybeExistingPredicate
            .map(ProgramPredicateConfigureView::getOneRowOfLeafNodes)
            .orElse(ImmutableMap.of());

    int columnNumber = 0;
    for (var qd : questionDefinitions) {
      Optional<LeafExpressionNode> maybeLeafNode =
          Optional.ofNullable(questionIdLeafNodeMap.get(qd.getId()));

      container.with(
          div(
                  div()
                      .with(TextFormatter.formatTextForAdmins(qd.getQuestionText().getDefault()))
                      .withClasses(
                          BaseStyles.INPUT,
                          "text-gray-500",
                          "mb-2",
                          "truncate",
                          ReferenceClasses.PREDICATE_QUESTION_NAME_FIELD)
                      .withData("testid", qd.getName())
                      .withData("question-id", String.valueOf(qd.getId())),
                  createScalarDropdown(qd, maybeLeafNode),
                  createOperatorDropdown(qd, maybeLeafNode))
              .withClasses(COLUMN_WIDTH, "shrink-0", iff(columnNumber++ != 0, "ml-16")));
    }

    return container.with(div().withClasses("w-28"));
  }

  private DivTag renderValueRow(
      ImmutableList<QuestionDefinition> questionDefinitions,
      int groupId,
      Optional<AndNode> maybeAndNode) {
    DivTag innerRow = div().withClasses("flex");
    DivTag row = div(innerRow).withClasses("flex", "mb-6", "predicate-config-value-row");
    DivTag andText = div("and").withClasses("object-center", "w-16", "p-4", "leading-10");

    ImmutableMap<Long, LeafExpressionNode> questionIdLeafNodeMap =
        maybeAndNode.map(AndNode::children).orElse(ImmutableList.of()).stream()
            .map(PredicateExpressionNode::getLeafNode)
            .collect(
                ImmutableMap.toImmutableMap(LeafExpressionNode::questionId, Function.identity()));

    int columnNumber = 1;
    for (QuestionDefinition questionDefinition : questionDefinitions) {
      Optional<LeafExpressionNode> maybeLeafNode =
          Optional.ofNullable(questionIdLeafNodeMap.get(questionDefinition.getId()));
      innerRow
          .condWith(columnNumber++ != 1, andText)
          .with(createValueField(questionDefinition, groupId, maybeLeafNode));
    }

    DivTag delete =
        div(Icons.svg(Icons.DELETE).withClasses("w-8"))
            .attr("role", "button")
            .withClasses(
                "predicate-config-delete-value-row",
                "mx-6",
                "w-12",
                "pt-2",
                "cursor-pointer",
                iff(groupId == 1, "hidden"));

    return row.with(delete);
  }

  // TODO: Refactor shared styles between this and
  // ProgramBlockPredicatesEditViewV2.renderPredicateQuestionCheckBoxRow.
  private DivTag renderQuestionCard(QuestionDefinition questionDefinition) {
    String questionHelpText =
        questionDefinition.getQuestionHelpText().isEmpty()
            ? ""
            : questionDefinition.getQuestionHelpText().getDefault();

    return div()
        .withClasses("my-4", "p-4", "flex", "flex-row", "gap-4", "border", "border-gray-300")
        .with(
            div(Icons.questionTypeSvg(questionDefinition.getQuestionType())
                    .withClasses("shrink-0", "h-12", "w-6"))
                .withClasses("flex", "items-center"))
        .with(
            div()
                .withClasses("text-left")
                .with(
                    div()
                        .with(
                            TextFormatter.formatTextForAdmins(
                                questionDefinition.getQuestionText().getDefault()))
                        .withClasses("font-bold"),
                    div()
                        .with(TextFormatter.formatTextForAdmins(questionHelpText))
                        .withClasses("mt-1", "text-sm"),
                    div(String.format("Admin ID: %s", questionDefinition.getName()))
                        .withClasses("mt-1", "text-sm")));
  }

  private DivTag createScalarDropdown(
      QuestionDefinition questionDefinition, Optional<LeafExpressionNode> maybeLeafNode) {
    Optional<Scalar> maybeSelectedScalar;
    ImmutableSet<Scalar> scalars;

    if (questionDefinition.isAddress()) {
      scalars = ImmutableSet.of(Scalar.SERVICE_AREAS);
      maybeSelectedScalar = Optional.of(Scalar.SERVICE_AREAS);
    } else {
      try {
        scalars = Scalar.getScalars(questionDefinition.getQuestionType());
        if (questionDefinition.getQuestionType().equals(QuestionType.NAME)) {
          // Name suffix is not included in eligibility predicates.
          scalars = ImmutableSet.of(Scalar.FIRST_NAME, Scalar.MIDDLE_NAME, Scalar.LAST_NAME);
        }
        maybeSelectedScalar =
            assertLeafOperationNode(maybeLeafNode).map(LeafOperationExpressionNode::scalar);
      } catch (InvalidQuestionTypeException | UnsupportedQuestionTypeException e) {
        // This should never happen since we filter out Enumerator questions before this point.
        return div()
            .withText("Sorry, you cannot create a show/hide predicate with this question type.");
      }
    }

    ImmutableList<OptionTag> options =
        scalars.stream()
            .map(
                scalar -> {
                  OptionTag tag =
                      option(scalar.toDisplayString())
                          .withValue(scalar.name())
                          // Add the scalar type as data so we can determine which operators to
                          // allow.
                          .withData("type", scalar.toScalarType().name());

                  if (maybeSelectedScalar.isPresent()
                      && maybeSelectedScalar.get().name().equals(scalar.name())) {
                    tag.isSelected();
                  }

                  return tag;
                })
            .collect(toImmutableList());

    var selectElement =
        new SelectWithLabel()
            .setFieldName(String.format("question-%d-scalar", questionDefinition.getId()))
            .setCustomOptions(options)
            .addReferenceClass(ReferenceClasses.PREDICATE_SCALAR_SELECT);

    if (scalars.size() == 1) {
      options.stream().findFirst().get().isSelected();
    }

    return selectElement
        .getSelectTag()
        .withData("question-id", String.valueOf(questionDefinition.getId()));
  }

  private Optional<LeafOperationExpressionNode> assertLeafOperationNode(
      Optional<LeafExpressionNode> maybeLeafNode) {
    if (maybeLeafNode.isPresent()
        && !maybeLeafNode.get().getType().equals(PredicateExpressionNodeType.LEAF_OPERATION)) {
      throw new IllegalArgumentException(
          String.format(
              "Expected LEAF_OPERATION node but received %s", maybeLeafNode.get().getType()));
    }

    return maybeLeafNode.map(node -> (LeafOperationExpressionNode) node);
  }

  private Optional<LeafAddressServiceAreaExpressionNode> assertLeafAddressServiceAreaNode(
      Optional<LeafExpressionNode> maybeLeafNode) {
    if (maybeLeafNode.isPresent()
        && !maybeLeafNode
            .get()
            .getType()
            .equals(PredicateExpressionNodeType.LEAF_ADDRESS_SERVICE_AREA)) {
      throw new IllegalArgumentException(
          String.format(
              "Expected LEAF_ADDRESS_SERVICE_AREA node but received %s",
              maybeLeafNode.get().getType()));
    }

    return maybeLeafNode.map(node -> (LeafAddressServiceAreaExpressionNode) node);
  }

  private DivTag createOperatorDropdown(
      QuestionDefinition questionDefinition, Optional<LeafExpressionNode> maybeLeafNode) {
    Optional<Scalar> maybeSelectedScalar;
    Optional<Operator> maybeSelectedOperator;

    if (questionDefinition.isAddress()) {
      Optional<LeafAddressServiceAreaExpressionNode> maybeLeafAddressServiceAreaExpressionNode =
          assertLeafAddressServiceAreaNode(maybeLeafNode);
      maybeSelectedScalar = Optional.of(Scalar.SERVICE_AREAS);
      maybeSelectedOperator =
          maybeLeafAddressServiceAreaExpressionNode.map(
              LeafAddressServiceAreaExpressionNode::operator);
    } else {
      try {
        ImmutableSet<Scalar> scalars = Scalar.getScalars(questionDefinition.getQuestionType());
        Optional<LeafOperationExpressionNode> maybeLeafOperationNode =
            assertLeafOperationNode(maybeLeafNode);
        maybeSelectedScalar = maybeLeafOperationNode.map(LeafOperationExpressionNode::scalar);
        maybeSelectedOperator = maybeLeafOperationNode.map(LeafOperationExpressionNode::operator);

        if (scalars.size() == 1) {
          maybeSelectedScalar = scalars.stream().findFirst();
        }
      } catch (InvalidQuestionTypeException | UnsupportedQuestionTypeException e) {
        throw new RuntimeException(e);
      }
    }

    // Copying to a final variable because variables referenced within
    // lambdas must be final or effectively final.
    final Optional<Scalar> finalMaybeSelectedScalar = maybeSelectedScalar;
    ImmutableList<OptionTag> operatorOptions =
        Arrays.stream(Operator.values())
            .map(
                operator -> {
                  // Add this operator's allowed scalar types as data, so that we can determine
                  // whether to show or hide each operator based on the current type of scalar
                  // selected.
                  OptionTag optionTag =
                      option(operator.toDisplayString()).withValue(operator.name());
                  operator
                      .getOperableTypes()
                      .forEach(
                          type -> optionTag.withData(type.name().toLowerCase(Locale.ROOT), ""));

                  if (maybeSelectedOperator.isPresent()
                      && operator.name().equals(maybeSelectedOperator.get().name())) {
                    optionTag.isSelected();
                  }

                  finalMaybeSelectedScalar.ifPresent(
                      selectedScalar -> {
                        if (!operator.getOperableTypes().contains(selectedScalar.toScalarType())) {
                          optionTag.withClass("hidden");
                        }
                      });

                  return optionTag;
                })
            .collect(toImmutableList());

    return new SelectWithLabel()
        .setFieldName(String.format("question-%d-operator", questionDefinition.getId()))
        .setCustomOptions(operatorOptions)
        .addReferenceClass(ReferenceClasses.PREDICATE_OPERATOR_SELECT)
        .getSelectTag()
        .withData("question-id", String.valueOf(questionDefinition.getId()));
  }

  private DivTag createValueField(
      QuestionDefinition questionDefinition,
      int groupId,
      Optional<LeafExpressionNode> maybeLeafNode) {
    DivTag valueField = div().withClasses(COLUMN_WIDTH);

    if (questionDefinition.getQuestionType().equals(QuestionType.ADDRESS)) {
      // Address questions only support service area predicates.

      Optional<String> maybeServiceArea =
          assertLeafAddressServiceAreaNode(maybeLeafNode)
              .map(LeafAddressServiceAreaExpressionNode::serviceAreaId);

      ImmutableCollection<EsriServiceAreaValidationOption> serviceAreaOptions =
          esriServiceAreaValidationConfig.getImmutableMap().values();

      SelectWithLabel select =
          new SelectWithLabel()
              .setFieldName(
                  String.format(
                      "group-%d-question-%d-predicateValue", groupId, questionDefinition.getId()))
              .setOptions(
                  serviceAreaOptions.stream()
                      .map(
                          option ->
                              SelectWithLabel.OptionValue.builder()
                                  .setLabel(option.getLabel())
                                  .setValue(option.getId())
                                  .build())
                      .collect(ImmutableList.toImmutableList()));

      if (maybeServiceArea.isPresent()) {
        select.setValue(maybeServiceArea.get());
      } else if (serviceAreaOptions.size() == 1) {
        select.setValue(serviceAreaOptions.stream().findFirst().get().getId());
      }

      return valueField
          .with(select.getSelectTag())
          .withData("question-id", String.valueOf(questionDefinition.getId()));
    } else if (questionDefinition.getQuestionType().isMultiOptionType()) {
      // If it's a multi-option question, we need to provide a discrete list of possible values to
      // choose from instead of a freeform text field. Not only is it a better UX, but we store the
      // ID of the options rather than the display strings since the option display strings are
      // localized.
      ImmutableList<QuestionOption> options =
          ((MultiOptionQuestionDefinition) questionDefinition).getOptions();

      ImmutableSet<String> currentlyCheckedValues =
          assertLeafOperationNode(maybeLeafNode)
              .map(LeafOperationExpressionNode::comparedValue)
              .map(PredicateValue::value)
              .map(
                  value ->
                      Splitter.on(", ")
                          .splitToStream(value.substring(1, value.length() - 1))
                          .map(item -> item.replaceAll("\"", ""))
                          .collect(ImmutableSet.toImmutableSet()))
              .orElse(ImmutableSet.of());

      for (QuestionOption option : options) {
        boolean isChecked = currentlyCheckedValues.contains(String.valueOf(option.id()));

        LabelTag optionCheckbox =
            FieldWithLabel.checkbox()
                .setFieldName(
                    String.format(
                        "group-%d-question-%d-predicateValues[]",
                        groupId, questionDefinition.getId()))
                .setValue(String.valueOf(option.id()))
                .setLabelText(option.optionText().getDefault())
                .setChecked(isChecked)
                .getCheckboxTag();
        valueField.with(optionCheckbox);
      }

      return valueField.withData("question-id", String.valueOf(questionDefinition.getId()));
    }

    Optional<LeafOperationExpressionNode> maybeLeafOperationNode =
        assertLeafOperationNode(maybeLeafNode);

    FormattedPredicateValue formattedValue =
        FormattedPredicateValue.fromLeafNode(maybeLeafOperationNode);
    boolean hasOneValue = formattedValue.isSingleValue();

    return valueField
        .withData("question-id", String.valueOf(questionDefinition.getId()))
        .with(
            FieldWithLabel.input()
                .setFieldName(
                    String.format(
                        "group-%d-question-%d-predicateValue", groupId, questionDefinition.getId()))
                .setValue(formattedValue.mainValue())
                .addReferenceClass(ReferenceClasses.PREDICATE_VALUE_INPUT)
                .getInputTag())
        .with(
            div()
                .withClasses(
                    ReferenceClasses.PREDICATE_VALUE_SECOND_INPUT_CONTAINER,
                    iff(hasOneValue, "hidden"))
                .with(div("and").withClasses("text-center"))
                .with(
                    FieldWithLabel.input()
                        .setFieldName(
                            String.format(
                                "group-%d-question-%d-predicateSecondValue",
                                groupId, questionDefinition.getId()))
                        .setValue(formattedValue.secondValue())
                        .setDisabled(hasOneValue)
                        .addReferenceClass(ReferenceClasses.PREDICATE_VALUE_INPUT)
                        .getInputTag()));
  }

  @Override
  protected ProgramDisplayType getProgramDisplayStatus() {
    return DRAFT;
  }
}
