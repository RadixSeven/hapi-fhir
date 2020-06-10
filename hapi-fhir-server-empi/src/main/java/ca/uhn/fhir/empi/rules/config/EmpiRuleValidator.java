package ca.uhn.fhir.empi.rules.config;

/*-
 * #%L
 * HAPI FHIR - Enterprise Master Patient Index
 * %%
 * Copyright (C) 2014 - 2020 University Health Network
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import ca.uhn.fhir.context.ConfigurationException;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.empi.rules.json.EmpiFieldMatchJson;
import ca.uhn.fhir.empi.rules.json.EmpiFilterSearchParamJson;
import ca.uhn.fhir.empi.rules.json.EmpiResourceSearchParamJson;
import ca.uhn.fhir.empi.rules.json.EmpiRulesJson;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.rest.server.util.ISearchParamRetriever;
import ca.uhn.fhir.util.FhirTerser;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Set;

@Service
public class EmpiRuleValidator {
	private static final Logger ourLog = LoggerFactory.getLogger(EmpiRuleValidator.class);

	// FIXME KHS remove
	private final FhirContext myFhirContext;
	private final ISearchParamRetriever mySearchParamRetriever;
	private final Class<? extends IBaseResource> myPatientClass;
	private final Class<? extends IBaseResource> myPractitionerClass;
	private final FhirTerser myTerser;

	@Autowired
	public EmpiRuleValidator(FhirContext theFhirContext, ISearchParamRetriever theSearchParamRetriever) {
		myFhirContext = theFhirContext;
		myPatientClass = theFhirContext.getResourceDefinition("Patient").getImplementingClass();
		myPractitionerClass = theFhirContext.getResourceDefinition("Practitioner").getImplementingClass();
		myTerser = theFhirContext.newTerser();
		mySearchParamRetriever = theSearchParamRetriever;
	}

	public void validate(EmpiRulesJson theEmpiRulesJson) {
		validateSearchParams(theEmpiRulesJson);
		validateMatchFields(theEmpiRulesJson);
		validateSystemIsUri(theEmpiRulesJson);
	}

	private void validateSearchParams(EmpiRulesJson theEmpiRulesJson) {
		for (EmpiResourceSearchParamJson searchParam : theEmpiRulesJson.getCandidateSearchParams()) {
			validateSearchParam("candidateSearchParams", searchParam.getResourceType(), searchParam.getSearchParam());
		}
		for (EmpiFilterSearchParamJson filter : theEmpiRulesJson.getCandidateFilterSearchParams()) {
			validateSearchParam("candidateFilterSearchParams", filter.getResourceType(), filter.getSearchParam());
		}
	}

	private void validateSearchParam(String theFieldName, String theTheResourceType, String theTheSearchParam) {
		if ("*".equals(theTheResourceType)) {
			validateResourceSearchParam(theFieldName, "Patient", theTheSearchParam);
			validateResourceSearchParam(theFieldName, "Practitioner", theTheSearchParam);
		} else {
			validateResourceSearchParam(theFieldName, theTheResourceType, theTheSearchParam);
		}
	}

	private void validateResourceSearchParam(String theFieldName, String theResourceType, String theSearchParam) {
		if (mySearchParamRetriever.getActiveSearchParam(theResourceType, theSearchParam) == null) {
			throw new ConfigurationException("Error in " + theFieldName + ": " + theResourceType + " does not have a search parameter called '" + theSearchParam + "'");
		}
	}

	private void validateMatchFields(EmpiRulesJson theEmpiRulesJson) {
		Set<String> names = new HashSet<>();
		for (EmpiFieldMatchJson fieldMatch : theEmpiRulesJson.getMatchFields()) {
			if (names.contains(fieldMatch.getName())) {
				throw new ConfigurationException("Two MatchFields have the same name '" + fieldMatch.getName() + "'");
			}
			names.add(fieldMatch.getName());
			validateThreshold(fieldMatch);
			validatePath(fieldMatch);
		}
	}

	private void validateThreshold(EmpiFieldMatchJson theFieldMatch) {
		if (theFieldMatch.getMetric().isSimilarity()) {
			if (theFieldMatch.getMatchThreshold() == null) {
				throw new ConfigurationException("MatchField " + theFieldMatch.getName() + " metric " + theFieldMatch.getMetric() + " requires a matchThreshold");
			}
		} else if (theFieldMatch.getMatchThreshold() != null) {
			throw new ConfigurationException("MatchField " + theFieldMatch.getName() + " metric " + theFieldMatch.getMetric() + " should not have a matchThreshold");
		}
	}

	// FIXME KHS validate the other parts of the rules
	private void validatePath(EmpiFieldMatchJson theFieldMatch) {
		String resourceType = theFieldMatch.getResourceType();
		if ("*".equals(resourceType)) {
			validatePatientPath(theFieldMatch);
			// FIXME KHS test where one matches and the other doesnt
			validatePractitionerPath(theFieldMatch);
		} else if ("Patient".equals(resourceType)) {
			validatePatientPath(theFieldMatch);
		} else if ("Practitioner".equals(resourceType)) {
			validatePractitionerPath(theFieldMatch);
		} else {
			// FIXME KHS test
			throw new ConfigurationException("MatchField " + theFieldMatch.getName() + " has unknown resourceType " + resourceType);
		}
	}

	private void validatePatientPath(EmpiFieldMatchJson theFieldMatch) {
		try {
			myTerser.getDefinition(myPatientClass, "Patient." + theFieldMatch.getResourcePath());
		} catch (DataFormatException|ConfigurationException e) {
			throw new ConfigurationException("MatchField " +
				theFieldMatch.getName() +
				" resourceType " +
				theFieldMatch.getResourceType() +
				" has invalid path '" + theFieldMatch.getResourcePath() + "'.  " +
				e.getMessage());
		}
	}

	private void validatePractitionerPath(EmpiFieldMatchJson theFieldMatch) {
		try {
			myTerser.getDefinition(myPractitionerClass, "Practitioner." + theFieldMatch.getResourcePath());
		} catch (DataFormatException e) {
			throw new ConfigurationException("MatchField " +
				theFieldMatch.getName() +
				" resourceType " +
				theFieldMatch.getResourceType() +
				" has invalid path '" + theFieldMatch.getResourcePath() + "'.  " +
				e.getMessage());
		}
	}

	private void validateSystemIsUri(EmpiRulesJson theEmpiRulesJson) {
		if (theEmpiRulesJson.getEnterpriseEIDSystem() == null) {
			return;
		}

		try {
			new URI(theEmpiRulesJson.getEnterpriseEIDSystem());
		} catch (URISyntaxException e) {
			throw new ConfigurationException("Enterprise Identifier System (eidSystem) must be a valid URI");
		}
	}
}
