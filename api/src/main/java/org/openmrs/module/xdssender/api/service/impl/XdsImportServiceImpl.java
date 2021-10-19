package org.openmrs.module.xdssender.api.service.impl;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpResponse;
import org.codehaus.jackson.map.ObjectMapper;
import org.dcm4chee.xds2.common.exception.XDSException;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.module.xdssender.XdsSenderConfig;
import org.openmrs.module.xdssender.api.domain.Ccd;
import org.openmrs.module.xdssender.api.errorhandling.CcdErrorHandlingService;
import org.openmrs.module.xdssender.api.errorhandling.ErrorHandlingService;
import org.openmrs.module.xdssender.api.errorhandling.RetrieveAndSaveCcdParameters;
import org.openmrs.module.xdssender.api.service.XdsImportService;
import org.openmrs.module.xdssender.api.xds.RestRetriever;
import org.openmrs.module.xdssender.api.xds.XdsRetriever;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component("xdsSender.XdsImportService")
public class XdsImportServiceImpl implements XdsImportService {
	
	private static final Log LOGGER = LogFactory.getLog(XdsImportServiceImpl.class);

	private static final String ECID_NAME = "ECID";
	private static final String PATIENT_ID_NAME = "Patient Identifier";
	
	@Autowired
	private XdsSenderConfig config;
	
	@Autowired
	private XdsRetriever xdsRetriever;

	@Autowired
	private RestRetriever restRetriever;

	@Override
	public Ccd retrieveCCD(Patient patient) throws XDSException {
		Ccd ccd = null;
		
		String patientEcid = extractPatientEcid(patient);
		CcdHttpResult result = xdsRetriever.sendRetrieveCCD(patientEcid);

		if (result.inError()) {
			ErrorHandlingService errorHandler = config.getCcdErrorHandlingService();
			if (errorHandler != null) {
				errorHandler.handle(
						prepareParameters(patient),
						CcdErrorHandlingService.RETRIEVE_AND_SAVE_CCD_DESTINATION,
						true,
						ExceptionUtils.getFullStackTrace(result.getException()));
			}
		} else {
			try {
				HttpResponse response = result.getResponse();
				String content = IOUtils.toString(response.getEntity().getContent());

				ccd = new Ccd();
				ccd.setPatient(patient);

				ccd.setDocument(content);
			} catch (IOException e) {
				LOGGER.error("Unable to load CCD content", e);
			}
		}

		return ccd;
	}

	@Override
	public String retrieveSHRObs(Patient patient) {
		String obsResult = null;

		String patientIdenditier = extractPatientIdentifier(patient);
		RestHttpResult result = restRetriever.sendRetrieveObs(patientIdenditier);

		if (result.inError()) {
			ErrorHandlingService errorHandler = config.getCcdErrorHandlingService();
			if (errorHandler != null) {
				errorHandler.handle(
						prepareParameters(patient),
						CcdErrorHandlingService.RETRIEVE_AND_SAVE_CCD_DESTINATION,
						true,
						ExceptionUtils.getFullStackTrace(result.getException()));
			}
		} else {
			try {
				HttpResponse response = result.getResponse();
				String content = IOUtils.toString(response.getEntity().getContent());

				obsResult = content;
			} catch (IOException e) {
				LOGGER.error("Unable to load SHR Obs content", e);
			}
		}

		return obsResult;
	}

	@Override
	public String retrieveStrSHRObs(String patientIdenditier) {
		// TEMPORARY METHOD FOR TESTING REST ENDPOINT!!!!!
		String obsResult = null;

		RestHttpResult result = restRetriever.sendRetrieveObs(patientIdenditier);
		if (result.inError()) {
			ErrorHandlingService errorHandler = config.getCcdErrorHandlingService();
			if (errorHandler != null) {
				errorHandler.handle(
						patientIdenditier,
						CcdErrorHandlingService.RETRIEVE_AND_SAVE_CCD_DESTINATION,
						true,
						ExceptionUtils.getFullStackTrace(result.getException()));
			}
		} else {
			try {
				HttpResponse response = result.getResponse();
				String content = IOUtils.toString(response.getEntity().getContent());

				obsResult = content;
			} catch (IOException e) {
				LOGGER.error("Unable to load SHR Obs content", e);
			}
		}

		return obsResult;
	}

	private String extractPatientEcid(Patient patient) {
		String patientEcid = null;
		for (PatientIdentifier patientIdentifier : patient.getIdentifiers()) {
			if (patientIdentifier.getIdentifierType().getName().equals(ECID_NAME)) {
				patientEcid = patientIdentifier.getIdentifier();
			}
		}
		return patientEcid;
	}

	private String extractPatientIdentifier(Patient patient) {
		String patientID = null;
		for (PatientIdentifier patientIdentifier : patient.getIdentifiers()) {
			if (patientIdentifier.getIdentifierType().getName().equals(PATIENT_ID_NAME)) {
				patientID = patientIdentifier.getIdentifier();
			}
		}
		return patientID;
	}

	private String prepareParameters(Patient patient) {
		RetrieveAndSaveCcdParameters parameters =
				new RetrieveAndSaveCcdParameters(patient.getUuid());
		try {
			return new ObjectMapper().writeValueAsString(parameters);
		} catch (IOException e) {
			throw new RuntimeException("Cannot prepare parameters for OutgoingMessageException", e);
		}
	}
}
