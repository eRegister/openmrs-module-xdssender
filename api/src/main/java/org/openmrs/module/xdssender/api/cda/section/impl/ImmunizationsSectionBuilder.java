package org.openmrs.module.xdssender.api.cda.section.impl;

import org.marc.everest.datatypes.II;
import org.marc.everest.datatypes.generic.CE;
import org.marc.everest.datatypes.generic.LIST;
import org.marc.everest.rmim.uv.cdar2.pocd_mt000040uv.Entry;
import org.marc.everest.rmim.uv.cdar2.pocd_mt000040uv.Section;
import org.openmrs.module.xdssender.XdsSenderConstants;
import org.springframework.stereotype.Component;

/**
 * Immunizations section builder
 * 
 * @author JustinFyfe
 */
@Component("xdssender.ImmunizationsSectionBuilder")
public class ImmunizationsSectionBuilder extends SectionBuilderImpl {
	
	/**
	 * Generate immunizations section
	 */
	@Override
	public Section generate(Entry... entries) {
		Section retVal = super.generate(entries);
		retVal.setTemplateId(LIST.createLIST(new II(XdsSenderConstants.SCT_TEMPLATE_CCD_IMMUNIZATIONS), new II(
		        XdsSenderConstants.SCT_TEMPLATE_IMMUNIZATIONS)));
		retVal.setTitle("History of Immunizations");
		retVal.setCode(new CE<String>("11369-6", XdsSenderConstants.CODE_SYSTEM_LOINC,
		        XdsSenderConstants.CODE_SYSTEM_NAME_LOINC, null, "HISTORY OF IMMUNIZATIONS", null));
		return retVal;
	}
	
}
