package org.openmrs.module.xdssender.api.cda.entry;

import org.marc.everest.rmim.uv.cdar2.pocd_mt000040uv.ClinicalStatement;
import org.openmrs.BaseOpenmrsData;

/**
 * Entry generator
 * 
 * @author JustinFyfe
 */
public interface EntryBuilder {
	
	/**
	 * Generate entry based on OpenMRS data
	 * 
	 * @param data
	 * @return
	 */
	ClinicalStatement generate(BaseOpenmrsData data);
}
