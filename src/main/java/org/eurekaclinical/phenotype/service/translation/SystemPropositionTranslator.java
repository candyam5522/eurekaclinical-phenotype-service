/*
 * #%L
 * Eureka Services
 * %%
 * Copyright (C) 2012 - 2013 Emory University
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
package org.eurekaclinical.phenotype.service.translation;

import java.util.ArrayList;
import java.util.List;

import org.protempa.PropertyDefinition;
import org.protempa.PropositionDefinition;

import com.google.inject.Inject;
import org.eurekaclinical.eureka.client.comm.SourceConfigParams;
import org.eurekaclinical.eureka.client.comm.SystemPhenotype;
//import org.eurekaclinical.phenotype.service.entity.SystemProposition;
import org.eurekaclinical.eureka.client.comm.exception.PhenotypeHandlingException;
import org.eurekaclinical.phenotype.service.finder.PropositionFindException;
import org.eurekaclinical.phenotype.service.finder.SystemPropositionFinder;
import org.eurekaclinical.phenotype.service.resource.SourceConfigResource;
import org.eurekaclinical.phenotype.service.util.PropositionUtil;
import javax.ws.rs.core.Response;
import org.eurekaclinical.phenotype.service.dao.PhenotypeEntityDao;
import org.eurekaclinical.phenotype.service.entity.SystemProposition;

import org.eurekaclinical.standardapis.exception.HttpStatusException;

public class SystemPropositionTranslator implements
		PropositionTranslator<SystemPhenotype, SystemProposition> {

	private final SystemPropositionFinder finder;
	private final SourceConfigResource sourceConfigResource;
	private final TranslatorSupport translatorSupport;

	@Inject
	public SystemPropositionTranslator(PhenotypeEntityDao inPropositionDao,
			SourceConfigResource inSourceConfigResource,
			SystemPropositionFinder inFinder) {
		finder = inFinder;
		this.sourceConfigResource = inSourceConfigResource;
		this.translatorSupport =
				new TranslatorSupport(inPropositionDao, inFinder, inSourceConfigResource);
	}

	@Override
	public SystemProposition translateFromPhenotype(SystemPhenotype phenotype)
			throws PhenotypeHandlingException {
		SystemProposition proposition =
				this.translatorSupport.getUserEntityInstance(phenotype,
				SystemProposition.class);
		proposition.setSystemType(phenotype.getSystemType());
		return proposition;
	}

	@Override
	public SystemPhenotype translateFromProposition(
			SystemProposition proposition) {
		SystemPhenotype phenotype = new SystemPhenotype();
		try {
			PropositionTranslatorUtil.populateCommonPhenotypeFields(phenotype,
					proposition);
			/*
			 * Hack to get an ontology source that assumes all Protempa configurations
			 * for a user point to the same knowledge source backends. This will go away.
			 */
			List<SourceConfigParams> scps = this.sourceConfigResource.getParamsList();
			if (scps.isEmpty()) {
				throw new HttpStatusException(Response.Status.INTERNAL_SERVER_ERROR, "No source configs");
			}
			PropositionDefinition propDef = finder.find(scps.get(0).getId(),
					proposition.getKey());
			List<SystemPhenotype> children = new ArrayList<>();
			for (String child : propDef.getInverseIsA()) {
				PropositionDefinition childDef = finder.find(
						scps.get(0).getId(), child);
				SystemPhenotype childPhenotype =
						PropositionUtil.toSystemPhenotype(scps.get(0).getId(), childDef, true,
						finder);

				children.add(childPhenotype);
			}
			phenotype.setChildren(children);

			List<String> properties = new ArrayList<>();
			for (PropertyDefinition property : propDef.getPropertyDefinitions()) {
				properties.add(property.getId());
			}
			phenotype.setProperties(properties);
			phenotype.setSystemType(proposition.getSystemType());
		} catch (PropositionFindException ex) {
			throw new AssertionError(
					"Error getting proposition definitions: " + ex.getMessage());
		}

		return phenotype;
	}
}
