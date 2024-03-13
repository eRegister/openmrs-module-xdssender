package org.openmrs.module.xdssender.api.cda;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.event.Event;
import org.openmrs.event.EventListener;
import org.openmrs.module.xdssender.XdsSenderConfig;
import org.openmrs.module.xdssender.api.errorhandling.ErrorHandlingService;
import org.openmrs.module.xdssender.api.errorhandling.ExportProvideAndRegisterParameters;
import org.openmrs.module.xdssender.api.errorhandling.XdsBErrorHandlingService;
import org.openmrs.module.xdssender.api.patient.PatientEcidUpdater;
import org.openmrs.module.xdssender.api.service.XdsExportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component("xdssender.EncounterEventListener")
public class EncounterEventListener implements EventListener {
    
    
	private static final Logger LOGGER = LoggerFactory.getLogger(EncounterEventListener.class);

	@Autowired
	private XdsSenderConfig config;

	@Autowired
	private PatientEcidUpdater ecidUpdater;
    
  
    private final ExecutorService executorService = Executors.newFixedThreadPool(10); 


    @Override
    public void onMessage(Message message) {
        try {
             MapMessage mapMessage = (MapMessage) message;
			 String messageAction = mapMessage.getString("action");
			
			 
            
            if (Event.Action.CREATED.toString().equals(messageAction)
                    || Event.Action.UPDATED.toString().equals(messageAction)) {
              
				executorService.submit(() -> {
                    try {
                        
						Context.openSession();
			            Context.authenticate(config.getOpenmrsUsername(), config.getOpenmrsPassword());
						String uuid = ((MapMessage) message).getString("uuid");
				        Encounter encounter = Context.getEncounterService().getEncounterByUuid(uuid);         
						
						if (encounter.getEncounterProviders().isEmpty()) {
					       LOGGER.warn("Skipped sending Encounter %s (Encounter already from HIE"
							+ "-> this is caused by the creating encounter)");
				        } 
						else if (!encounterHasSentinelEvents(encounter)) {
					        LOGGER.warn("Skipped sending Encounter %s (Encounter does not have any sentinel events"
							+ "-> this is caused by the creating encounter)");
						}
						else {
					          Patient patient = Context.getPatientService().getPatient(encounter.getPatient().getPatientId());
							
				           //commeting out this piece of code that fetch ECID from OPenEMPI after commeting the xdsProvideAndRegister code
                            //to stop looking for and ECID of a patient.			

				             //ecidUpdater.fetchEcidIfRequired(patient);
							 XdsExportService service = Context.getService(XdsExportService.class);
							 
						try {
						       service.exportProvideAndRegister(encounter, patient);
					        }
					    catch (Exception e) {

						       LOGGER.error("XDS export exception occurred", e);
						       ErrorHandlingService errorHandler = config.getXdsBErrorHandlingService();
						    if (errorHandler != null) {
							        LOGGER.error("XDS export exception occurred", e);
							           errorHandler.handle(
									   prepareParameters(encounter, patient),
									   XdsBErrorHandlingService.EXPORT_PROVIDE_AND_REGISTER_DESTINATION,
									true,
									   ExceptionUtils.getFullStackTrace(e));
						       }  
							else {
							        throw new RuntimeException("XDS export exception occurred "
									+ "with not configured XDS.b error handler", e);
						}
					   }
					  }
					}
					catch (Exception e) {
						
                        LOGGER.error("XDS export exception occurred", e);
                    }
				} 
                );
		}
		Context.closeSession();
		
		
		}
            
        
        catch (JMSException e) {
               System.out.println("Some error occurred" + e.getErrorCode());
            }
    }
	
	private String prepareParameters(Encounter encounter, Patient patient) {
		ExportProvideAndRegisterParameters parameters =
				new ExportProvideAndRegisterParameters(patient.getUuid(), encounter.getUuid());
		try {
			return new ObjectMapper().writeValueAsString(parameters);
		} catch (IOException e) {
			throw new RuntimeException("Cannot prepare parameters for OutgoingMessageException", e);
		}
	}

	private boolean encounterHasSentinelEvents(Encounter encounter) {
		Set<Obs> observations = encounter.getObs();
		for (Obs o : observations) {
			if (o.getConcept().getName().toString().equalsIgnoreCase("HTC, Final HIV status")
					|| o.getConcept().getName().toString().equalsIgnoreCase("HTC, Retest HIV status")
					|| o.getConcept().getName().toString().equalsIgnoreCase("HIVTC, ART start date")
					|| o.getConcept().getName().toString().equalsIgnoreCase("ART, Follow-up date")
					|| o.getConcept().getName().toString().equalsIgnoreCase("HIVTC, ART Regimen")
					|| o.getConcept().getName().toString().equalsIgnoreCase("HIVTC, Viral Load")
					|| o.getConcept().getName().toString().equalsIgnoreCase("HIVTC, Date transferred out")
					|| o.getConcept().getName().toString().equalsIgnoreCase("HIVTC, Date transferred in")) {
				return true;
			}
		}
		return false;
	}
  }

