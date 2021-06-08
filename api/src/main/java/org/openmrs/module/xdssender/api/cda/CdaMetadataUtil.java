package org.openmrs.module.xdssender.api.cda;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.marc.everest.datatypes.ED;
import org.marc.everest.datatypes.II;
import org.marc.everest.datatypes.NullFlavor;
import org.marc.everest.datatypes.generic.CD;
import org.marc.everest.datatypes.generic.CE;
import org.marc.everest.datatypes.generic.CS;
import org.marc.everest.datatypes.generic.CV;
import org.marc.everest.datatypes.generic.SET;
import org.openmrs.Concept;
import org.openmrs.ConceptMap;
import org.openmrs.ConceptReferenceTerm;
import org.openmrs.Location;
import org.openmrs.LocationAttribute;
import org.openmrs.Provider;
import org.openmrs.ProviderAttribute;
import org.openmrs.Visit;
import org.openmrs.VisitAttribute;
import org.openmrs.api.context.Context;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * The On-Demand document metadata cda
 */
@Component("xdssender.CdaMetadataUtil")
public class CdaMetadataUtil {
	
	private Log log = LogFactory.getLog(getClass());
	
	@Autowired
	private ConceptUtil conceptUtil;
	
	/**
	 * Get the specified visit attribute
	 */
	public VisitAttribute getVisitAttribute(Visit visit, String attributeName) {
		for (VisitAttribute att : visit.getActiveAttributes()) {
			if (att.getAttributeType().getName().equals(attributeName)) {
				return att;
			}
		}
		return null;
	}
	
	/**
	 * Get a location attribute
	 */
	public LocationAttribute getLocationAttribute(Location location, String attributeName) {
		for (LocationAttribute att : location.getActiveAttributes()) {
			if (att.getAttributeType().getName().equals(attributeName)) {
				return att;
			}
		}
		return null;
	}
	
	/**
	 * Get the provider attribute
	 */
	public ProviderAttribute getProviderAttribute(Provider pvdr, String attributeName) {
		for (ProviderAttribute att : pvdr.getActiveAttributes()) {
			if (att.getAttributeType().getName().equals(attributeName)) {
				return att;
			}
		}
		return null;
	}
	
	public <T extends CS> T getStandardizedCode(Concept value, String targetCodeSystem, Class<T> clazz) {
		
		try {
			T retVal;
			
			if (value == null) {
				retVal = clazz.newInstance();
				retVal.setNullFlavor(NullFlavor.NoInformation);
				return retVal;
			}
			// First, we need to find the reference term that represents the most applicable
			Queue<ConceptReferenceTerm> preferredCodes = new ArrayDeque<ConceptReferenceTerm>()
					, equivalentCodes = new ArrayDeque<ConceptReferenceTerm>()
					, narrowerCodes = new ArrayDeque<ConceptReferenceTerm>();
			
			// Mappings
			String targetCodeSystemName = this.conceptUtil.mapOidToConceptSourceName(targetCodeSystem);
			for (ConceptMap mapping : value.getConceptMappings()) {
				if (mapping.getConceptMapType().getName().equalsIgnoreCase("SAME-AS")) {
					ConceptReferenceTerm candidateTerm = mapping.getConceptReferenceTerm();
					if (targetCodeSystem == null || targetCodeSystemName.equals(candidateTerm.getConceptSource().getName())
					        || targetCodeSystem.equals(candidateTerm.getConceptSource().getHl7Code())) {
						preferredCodes.add(candidateTerm);
					} else {
						equivalentCodes.add(candidateTerm);
					}
				} else {
					ConceptReferenceTerm candidateTerm = mapping.getConceptReferenceTerm();
					if (targetCodeSystem == null || targetCodeSystemName.equals(candidateTerm.getConceptSource().getName())
					        || targetCodeSystem.equals(candidateTerm.getConceptSource().getHl7Code())) {
						narrowerCodes.add(candidateTerm);
					}
				}
			}
			
			// No SAME-AS but maybe a narrower code?
			if (preferredCodes.size() == 0) {
				if (narrowerCodes.size() > 0)
					preferredCodes.add(narrowerCodes.poll());
				else
					log.warn(String.format("Could not find code for %s in %s", value, targetCodeSystem));
			}
			// Now that we have a term, let's see if we can select a preferred term
			ConceptReferenceTerm preferredTerm = preferredCodes.poll();
			if (preferredTerm == null) {
				retVal = clazz.newInstance();
				retVal.setNullFlavor(NullFlavor.Other);
				if (retVal instanceof CV) {
					((CV<?>) retVal).setCodeSystem(targetCodeSystem);
					((CV<?>) retVal).setCodeSystemName(targetCodeSystemName);
				}
			} else {
				retVal = this.createCode(preferredTerm, clazz);
			}
			
			if (retVal instanceof CV) {
				if (value.getPreferredName(Context.getLocale()) != null) {
					((CV<?>) retVal).setOriginalText(new ED(value.getPreferredName(Context.getLocale()).getName(), null));
				} else if (value.getName() != null) {
					((CV<?>) retVal).setOriginalText(new ED(value.getName().getName(), null));
				} else if (value.getDescription() != null) {
					((CV<?>) retVal).setOriginalText(new ED(value.getDescription().getDescription(), null));
				}
			}
			
			// Are there other preferred terms
			if (retVal instanceof CE) {
				SET<CD<?>> translations = new SET<CD<?>>();
				while (preferredCodes.peek() != null) {
					preferredTerm = preferredCodes.poll();
					CD<?> trans = this.createCode(preferredTerm, CD.class);
					if (trans != null) {
						translations.add(trans);
					}
				}
				
				// Fallback to others if we're going for broke
				if (retVal.isNull()) {
					while (equivalentCodes.peek() != null) {
						preferredTerm = equivalentCodes.poll();
						
						// Does the equivalent code have an oid?
						CD<?> trans = this.createCode(preferredTerm, CD.class);
						if (trans != null) {
							translations.add(trans);
						}
					}
				}
				
				// Add translations if any
				if (!translations.isEmpty()) {
					((CE) retVal).setTranslation(translations);
				}
			}
			
			return retVal;
		}
		catch (Exception e) {
			log.error("Error creating code", e);
			return null;
		}
		
	}
	
	/**
	 * Create the actual code data from the referenceTerm
	 */
	private <T extends CS> T createCode(ConceptReferenceTerm referenceTerm, Class<T> clazz) {
		try {
			T retVal = clazz.newInstance();
			retVal.setCode(referenceTerm.getCode());
			if (retVal instanceof CV) {
				
				if (referenceTerm.getDescription() == null) {
					((CV<?>) retVal).setDisplayName(referenceTerm.getName());
				} else {
					((CV<?>) retVal).setDisplayName(referenceTerm.getDescription());
				}
				
				((CV<?>) retVal).setCodeSystemName(referenceTerm.getConceptSource().getName());
				
				String codeSystemHl7 = referenceTerm.getConceptSource().getHl7Code();
				if (codeSystemHl7 != null && II.isRootOid(new II(codeSystemHl7))) {
					((CV<?>) retVal).setCodeSystem(conceptUtil.mapConceptSourceNameToOid(referenceTerm.getConceptSource()
					        .getHl7Code()));
				} else {
					codeSystemHl7 = conceptUtil.mapConceptSourceNameToOid(referenceTerm.getConceptSource().getName());
					if (II.isRootOid(new II(codeSystemHl7))) {
						((CV<?>) retVal).setCodeSystem(codeSystemHl7);
					} else {
						return null;
					}
				}
			}
			
			return retVal;
		}
		catch (Exception e) {
			log.error("Error creating code", e);
			return null;
		}
	}
	
}
