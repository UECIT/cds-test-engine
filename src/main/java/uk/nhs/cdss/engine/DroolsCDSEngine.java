package uk.nhs.cdss.engine;

import org.drools.core.impl.InternalKnowledgeBase;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.QueryResults;
import org.kie.api.runtime.rule.QueryResultsRow;
import org.springframework.stereotype.Component;
import uk.nhs.cdss.domain.Assertion;
import uk.nhs.cdss.domain.CarePlan;
import uk.nhs.cdss.domain.Questionnaire;
import uk.nhs.cdss.domain.QuestionnaireResponse;
import uk.nhs.cdss.domain.Redirection;
import uk.nhs.cdss.domain.Result;
import uk.nhs.cdss.domain.Result.Status;

@Component
public class DroolsCDSEngine implements CDSEngine {

  public static final String ASSERTIONS_QUERY = "assertions";
  public static final String ASSERTION_ID = "assertion";

  public static final String QUESTIONNAIRES_QUERY = "questionnaires";
  public static final String QUESTIONNAIRE_ID = "questionnaire";

  public static final String CAREPLANS_QUERY = "carePlans";
  public static final String CAREPLAN_ID = "carePlan";

  public static final String REDIRECTS_QUERY = "redirects";
  public static final String REDIRECT_ID = "redirect";

  private final CDSKnowledgeBaseFactory knowledgeBaseFactory;
  private final CodeDirectory codeDirectory;

  public DroolsCDSEngine(CDSKnowledgeBaseFactory knowledgeBaseFactory, CodeDirectory codeDirectory) {
    this.knowledgeBaseFactory = knowledgeBaseFactory;
    this.codeDirectory = codeDirectory;
  }

  @Override
  public CDSOutput evaluate(CDSInput input) throws ServiceDefinitionException {
    InternalKnowledgeBase kbase = knowledgeBaseFactory
        .getKnowledgeBase(input.getServiceDefinitionId());
    KieSession ksession = kbase.newKieSession();

    try {
      ksession.setGlobal("codeDirectory", codeDirectory);

      // Add encounter metadata
      if (input.getPatient() != null) {
        ksession.insert(input.getPatient());
      }


      System.out.println("---------------------------\nInput");

      // Add existing assertions
      input.getAssertions().stream()
          .filter(a -> a.getRelated().stream()
              .noneMatch(qr -> QuestionnaireResponse.Status.AMENDED.equals(qr.getStatus())))
          .peek(System.out::println)
          .forEach(ksession::insert);

      // Add all answers
      input.getResponses().forEach(response -> {
        ksession.insert(response);
        response.getAnswers().stream()
            .peek(System.out::println)
            .forEach(ksession::insert);
      });

      // Execute and collect results
      System.out.println();
      ksession.fireAllRules();
      CDSOutput output = new CDSOutput();

      System.out.println("\nOutput");
      // Query resulting assertions and questionnaires
      QueryResults assertions = ksession.getQueryResults(ASSERTIONS_QUERY);
      for (QueryResultsRow resultsRow : assertions) {
        System.out.println("Assertion " + resultsRow.get(ASSERTION_ID) + " added to output");
        output.getAssertions().add((Assertion) resultsRow.get(ASSERTION_ID));
      }

      QueryResults questionnaires = ksession.getQueryResults(QUESTIONNAIRES_QUERY);
      for (QueryResultsRow resultsRow : questionnaires) {
        System.out.println("Questionnaire " + resultsRow.get(QUESTIONNAIRE_ID) + " added to output");
        output.getQuestionnaireIds()
            .add(((Questionnaire) resultsRow.get(QUESTIONNAIRE_ID)).getId());
      }

      Result result = new Result("result", Status.SUCCESS);

      QueryResults carePlans = ksession.getQueryResults(CAREPLANS_QUERY);
      for (QueryResultsRow resultsRow : carePlans) {
        System.out.println("CarePlan " + resultsRow.get(CAREPLAN_ID) + " added to output");
        result.getCarePlanIds().add(((CarePlan) resultsRow.get(CAREPLAN_ID)).getId());
      }

      var redirects = ksession.getQueryResults(REDIRECTS_QUERY);
      if (redirects.size() > 1) {
        System.out.println("Invalid: more than 1 Redirect has been added");
      } else if (redirects.size() == 1) {
        var resultsRow = redirects.iterator().next();
        System.out.println("Redirect " + resultsRow.get(REDIRECT_ID) + " added to output");
        result.setRedirection((Redirection) resultsRow.get(REDIRECT_ID));
      }

      // Determine result
      boolean dataRequested = !output.getQuestionnaireIds().isEmpty();
      boolean hasCarePlans = !result.getCarePlanIds().isEmpty();
      boolean hasRedirection = result.getRedirection() != null;

      if (dataRequested) {
        if (hasCarePlans) {
          result.setStatus(Status.DATA_REQUESTED);
        } else {
          result.setStatus(Status.DATA_REQUIRED);
        }
      } else if (!hasCarePlans && !hasRedirection) {
        throw new IllegalStateException("Rules did not create care plan or request data");
      }
      output.setResult(result);

      return output;
    } finally {
      ksession.dispose();
    }
  }

}