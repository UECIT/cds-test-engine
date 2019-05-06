package uk.nhs.cdss.resourceProviders;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.hl7.fhir.dstu3.model.Enumerations.PublicationStatus;
import org.hl7.fhir.dstu3.model.GuidanceResponse;
import org.hl7.fhir.dstu3.model.IdType;
import org.hl7.fhir.dstu3.model.Parameters;
import org.hl7.fhir.dstu3.model.ServiceDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.RequiredParam;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.param.DateRangeParam;
import ca.uhn.fhir.rest.param.TokenAndListParam;
import ca.uhn.fhir.rest.param.TokenOrListParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import uk.nhs.cdss.entities.ServiceDefinitionEntity;
import uk.nhs.cdss.repos.ServiceDefinitionRepository;
import uk.nhs.cdss.resourceBuilder.ServiceDefinitionBuilder;
import uk.nhs.cdss.services.EvaluateService;

@Component
public class ServiceDefinitionProvider implements IResourceProvider {
	private static final String EVALUATE = "$evaluate";

	@Autowired
	private EvaluateService evaluateService;

	@Autowired
	private ServiceDefinitionBuilder serviceDefinitionBuilder;
	
	@Autowired 
	private ServiceDefinitionRepository serviceDefinitionRepository;

	@Override
	public Class<ServiceDefinition> getResourceType() {
		return ServiceDefinition.class;
	}

	@Operation(name = EVALUATE, idempotent = true)
	public GuidanceResponse evaluate(@IdParam IdType serviceDefinitionId, @ResourceParam Parameters params) {
		return evaluateService.getGuidanceResponse(params, serviceDefinitionId.getIdPartAsLong());
	}

	@Read
	public ServiceDefinition getServiceDefinitionById(@IdParam IdType serviceDefinitionId) {
		return serviceDefinitionBuilder.createServiceDefinition(serviceDefinitionId.getIdPartAsLong());
	}
	
	@Search
	public Collection<ServiceDefinition> findServiceDefinitions(
			@OptionalParam(name=ServiceDefinition.SP_STATUS) TokenParam status,
			@OptionalParam(name="experimental") TokenParam experimental,
			@OptionalParam(name=ServiceDefinition.SP_EFFECTIVE) DateRangeParam effective,
			@OptionalParam(name="useContext") TokenAndListParam useContext,
			@OptionalParam(name=ServiceDefinition.SP_JURISDICTION) TokenParam jurisdiction,
			@RequiredParam(name="trigger") TokenOrListParam trigger) {
	
		List<String> triggers = 
				trigger.getValuesAsQueryTokens().stream().map(TokenParam::getValue).collect(Collectors.toList());
		
		List<String> useContexts = useContext == null ? 
				new ArrayList<>() : useContext.getValuesAsQueryTokens().stream().map(tokenList -> 
					tokenList.getValuesAsQueryTokens().get(0).getValue()).collect(Collectors.toList());
				
		List<ServiceDefinitionEntity> entities = useContexts.isEmpty() ? 
				serviceDefinitionRepository.search(
					status == null ? null : PublicationStatus.valueOf(status.getValue().toUpperCase()), 
					effective == null ? null : effective.getLowerBoundAsInstant(), 
					effective == null ? null : effective.getUpperBoundAsInstant(), 
					jurisdiction == null ? null : jurisdiction.getValue().toUpperCase(), 
					triggers, experimental == null ? null : !"FALSE".equalsIgnoreCase(experimental.getValue())) :
				serviceDefinitionRepository.search(
					status == null ? null : PublicationStatus.valueOf(status.getValue().toUpperCase()), 
					effective == null ? null : effective.getLowerBoundAsInstant(), 
					effective == null ? null : effective.getUpperBoundAsInstant(), 
					jurisdiction == null ? null : jurisdiction.getValue().toUpperCase(), 
					useContexts, new Long(useContexts.size()), 
					triggers, experimental == null ? null : !"FALSE".equalsIgnoreCase(experimental.getValue()));			
		
		Collection<ServiceDefinition> serviceDefinitions = new ArrayList<>();
		
		if (!entities.isEmpty()) 
				entities.stream().forEach(entity -> 
					serviceDefinitions.add(serviceDefinitionBuilder.createServiceDefinition(entity, true)));
		
		return serviceDefinitions;
	}

}
