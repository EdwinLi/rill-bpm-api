/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.rill.bpm.ws.api.activiti;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Resource;
import javax.jws.HandlerChain;
import javax.jws.WebService;
import javax.servlet.ServletContext;
import javax.xml.ws.WebServiceContext;
import javax.xml.ws.handler.MessageContext;

import org.activiti.engine.impl.interceptor.Command;
import org.activiti.engine.impl.interceptor.CommandContext;
import org.activiti.engine.impl.persistence.entity.ExecutionEntity;
import org.activiti.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.activiti.engine.impl.persistence.entity.TaskEntity;
import org.activiti.engine.runtime.ProcessInstance;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.rill.bpm.api.WorkflowCache;
import org.rill.bpm.api.WorkflowOperations;
import org.rill.bpm.api.activiti.ActivitiAccessor;
import org.rill.bpm.api.exception.ProcessException;
import org.rill.bpm.api.scaleout.ScaleoutHelper;
import org.rill.bpm.ws.api.RemoteWorkflowOperations;
import org.springframework.context.ApplicationContext;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.web.context.support.WebApplicationContextUtils;

import com.sun.xml.ws.api.tx.at.Transactional;
import com.sun.xml.ws.api.tx.at.Transactional.Version;

/**
 * Web Service API for work flow.
 * <p>
 * Use @com.sun.xml.ws.api.tx.at.Transactional to mark some WS method is under
 * WS-AT specification.
 * 
 * @author mengran
 */
@WebService
@HandlerChain(file="HandlerChain.xml")
public class RemoteActivitiTemplate implements RemoteWorkflowOperations {

	/** Logger available to subclasses */
	protected final Log logger = LogFactory.getLog(getClass().getName());
    
	// Must use getter method!
	private volatile WorkflowOperations workflowAccessor;
	private volatile WorkflowCache<HashMap<String, String>> workflowCache;
//	private RobustActivitiTemplate activitiTemplate;
	private AtomicBoolean needRetrieve = new AtomicBoolean(true);
	// Use count down latch for lazy initialize. MENGRAN at 2012-03-11
	private CountDownLatch initializing = new CountDownLatch(1);

	@Resource
	private WebServiceContext context;

	@SuppressWarnings("unchecked")
	private WorkflowOperations getWorkflowAccessor() {
		if (needRetrieve.compareAndSet(true, false)) {
			ApplicationContext ac = WebApplicationContextUtils
					.getRequiredWebApplicationContext(
							(ServletContext) context.getMessageContext().get(
									MessageContext.SERVLET_CONTEXT));
			workflowAccessor = ac.getBean("workflowAccessor", WorkflowOperations.class);
			workflowCache = ac.getBean("workflowCache", WorkflowCache.class);
			logger.info(RemoteActivitiTemplate.class.getName() + " lazy initialize completed.");
			initializing.countDown();
		}
		
		try {
			initializing.await();
		} catch (InterruptedException e) {
			logger.error(e);
		}
		Assert.notNull(workflowAccessor);
		return workflowAccessor;
	}

	@Transactional(version = Version.WSAT10)
	public RemoteWorkflowResponse createProcessInstance(
			CreateProcessInstanceDto createProcessInstanceDto)
			throws ProcessException {

		Map<String, Object> passToEngine = new HashMap<String, Object>();
		if (createProcessInstanceDto.getStartParams() != null) {
			for(Entry<String, String> entry : createProcessInstanceDto.getStartParams().entrySet()) {
				passToEngine.put(entry.getKey(), entry.getValue());
			}
		}
		// Delegate this operations
		final List<String> tasks = getWorkflowAccessor().createProcessInstance(
				createProcessInstanceDto.getProcessDefinitionKey(),
				createProcessInstanceDto.getProcessStarter(),
				createProcessInstanceDto.getBusinessObjectId(),
				passToEngine);

		final String engineProcessInstanceId = getWorkflowAccessor().getEngineProcessInstanceIdByBOId(
				createProcessInstanceDto.getBusinessObjectId(), createProcessInstanceDto.getProcessDefinitionKey());

		// Directly use create process instance API means root process
		return new RemoteWorkflowResponse(engineProcessInstanceId,
				createProcessInstanceDto.getBusinessObjectId(),
				createProcessInstanceDto.getProcessDefinitionKey(), tasks, engineProcessInstanceId, (engineProcessInstanceId == null));
	}

	@Transactional(version = Version.WSAT10)
	public RemoteWorkflowResponse completeTaskInstance(
			final CompleteTaskInstanceDto completeTaskInstanceDto)
			throws ProcessException {

		final Map<String, Object> passToEngine = new HashMap<String, Object>();
		if (completeTaskInstanceDto.getWorkflowParams() != null) {
			for(Entry<String, String> entry : completeTaskInstanceDto.getWorkflowParams().entrySet()) {
				passToEngine.put(entry.getKey(), entry.getValue());
			}
		}
		
		// Initialize
		getWorkflowAccessor();
		
		WorkflowOperations impl = ScaleoutHelper.determineImplWithTaskInstanceId(workflowCache, workflowAccessor, completeTaskInstanceDto.getEngineTaskInstanceId());
		final RobustActivitiTemplate activitiTemplate = ActivitiAccessor.retrieveActivitiAccessorImpl(impl, RobustActivitiTemplate.class);
		
		return activitiTemplate
				.runExtraCommand(new Command<RemoteWorkflowResponse>() {

					@Override
					public RemoteWorkflowResponse execute(
							CommandContext commandContext) {

						TaskEntity taskEntity = commandContext
								.getTaskManager()
								.findTaskById(completeTaskInstanceDto.getEngineTaskInstanceId());
						// Handle retrieve
						if (taskEntity == null) {
							logger.warn("Can not find task instance by id " + completeTaskInstanceDto.getEngineTaskInstanceId() + ", maybe it has beed completed.");
							return activitiTemplate.handleTaskInstanceHasEnd(completeTaskInstanceDto.getEngineTaskInstanceId(), commandContext);
						}
						
						String engineProcessInstanceId = taskEntity.getProcessInstanceId();
						// Cache root process instance ID
						String rootProcessInstanceId = activitiTemplate.obtainRootProcess(engineProcessInstanceId, true);
						ExecutionEntity ee = commandContext
								.getExecutionManager().findExecutionById(
										engineProcessInstanceId);
						ProcessDefinitionEntity pde = commandContext
								.getProcessDefinitionManager()
								.findLatestProcessDefinitionById(
										ee.getProcessDefinitionId());
						String processDefinitionKey = pde.getKey();
						
						ExecutionEntity rootee = commandContext
								.getExecutionManager().findExecutionById(
										rootProcessInstanceId);
						String businessKey = rootee.getBusinessKey();
						
						// Delegate this operations
						final List<String> tasks = getWorkflowAccessor().completeTaskInstance(
								completeTaskInstanceDto.getEngineTaskInstanceId(),
								completeTaskInstanceDto.getOperator(),
								passToEngine);
						
						ProcessInstance processInstance = activitiTemplate.getRuntimeService().createProcessInstanceQuery().processInstanceId(engineProcessInstanceId).singleResult();
						return new RemoteWorkflowResponse(
								engineProcessInstanceId, businessKey,
								processDefinitionKey, tasks, rootProcessInstanceId, processInstance == null);
					}

				});

	}
	
	@Override
	@Transactional(version = Version.WSAT10)
	public void deleteProcessInstance(String engineProcessInstanceId,
			String reason) throws ProcessException {

		// Initialize
		getWorkflowAccessor();
				
		// Do delete operation
		logger.info("Call Activiti API to delete process instance: " + engineProcessInstanceId + " for " + ObjectUtils.getDisplayString(reason));
		workflowAccessor.terminalProcessInstance(engineProcessInstanceId, "via-ws", reason);
	}
	
	// ----------------------------------------- Read API as below ----------//

//	@Transactional(version = Version.WSAT10, enabled = false)
	public String getEngineProcessInstanceIdByBOId(String processDefinitionKey,
			String boId) {
		
		// Initialize
		getWorkflowAccessor();
		
		return workflowAccessor.getEngineProcessInstanceIdByBOId(boId, processDefinitionKey);
	}

	@Override
//	@Transactional(version = Version.WSAT10, enabled = false)
	public List<String[]> getTaskInstanceExtendAttrs(
			String engineTaskInstanceId) {
		
		// Delegate this operation
		Map<String, String> extendAttrMap = getWorkflowAccessor().getTaskInstanceInformations(engineTaskInstanceId);
		if (extendAttrMap == null) {
			return null;
		}
		List<String[]> forReturn = new ArrayList<String[]>(extendAttrMap.size());
		for (Entry<String, String> entry : extendAttrMap.entrySet()) {
			String[] keyValue = new String[] {entry.getKey(), entry.getValue()};
			forReturn.add(keyValue);
		}
		return forReturn;
	}

	@Override
//	@Transactional(version = Version.WSAT10, enabled = false)
	public String getRootProcessInstanceId(String engineProcessInstanceId) throws ProcessException {
		
		// Initialize
		getWorkflowAccessor();
		
		WorkflowOperations impl = ScaleoutHelper.determineImplWithProcessInstanceId(workflowCache, workflowAccessor, engineProcessInstanceId);
		final RobustActivitiTemplate activitiTemplate = ActivitiAccessor.retrieveActivitiAccessorImpl(impl, RobustActivitiTemplate.class);
		// Unique instance exists
		
		try {
			return activitiTemplate.obtainRootProcess(engineProcessInstanceId, true);
		} catch (Exception e) {
			throw new ProcessException("Maybe process" + engineProcessInstanceId + "is ended.", e);
		}
	}

	@Override
//	@Transactional(version = Version.WSAT10, enabled = false)
	public String[] getProcessInstanceVariableNames(
			String engineProcessInstanceId) {
		
		// Initialize
		getWorkflowAccessor();
		
		try {
			Set<String> processInstanceNames = workflowAccessor.getProcessInstanceVariableNames(engineProcessInstanceId);
			logger.debug("Retrieve process instance variable names: " + ObjectUtils.getDisplayString(processInstanceNames));
			return processInstanceNames == null ? null : processInstanceNames.toArray(new String[processInstanceNames.size()]); 
		} catch (Exception e) {
			throw new ProcessException("Maybe process" + engineProcessInstanceId + "is ended.", e);
		}
	}

	@Override
//	@Transactional(version = Version.WSAT10, enabled = false)
	public String[] getLastedVersionProcessDefinitionVariableNames(
			String processDefinitionKey) {
		
		// Initialize
		getWorkflowAccessor();
		
		try {
			Set<String> processInstanceNames = workflowAccessor.getLastedVersionProcessDefinitionVariableNames(processDefinitionKey);
			logger.debug("Retrieve process definition variable names: " + ObjectUtils.getDisplayString(processInstanceNames));
			return processInstanceNames == null ? null : processInstanceNames.toArray(new String[processInstanceNames.size()]); 
		} catch (Exception e) {
			throw new ProcessException("Can not get variables by process definition key " + processDefinitionKey, e);
		}
				
	}

	@Override
	@Transactional(version = Version.WSAT10)
	public RemoteWorkflowResponse[] batchCompleteTaskInstance(
			CompleteTaskInstanceDto[] completeTaskInstanceDtos)
			throws ProcessException {
		
		if (completeTaskInstanceDtos == null || completeTaskInstanceDtos.length == 0) {
			return null;
		}
		
		logger.info("Batch complete task instances start...");
		
		List<RemoteWorkflowResponse> batchResult = new ArrayList<RemoteWorkflowOperations.RemoteWorkflowResponse>();
		// Delegate this operation
		for (CompleteTaskInstanceDto dto : completeTaskInstanceDtos) {
			RemoteWorkflowResponse response = this.completeTaskInstance(dto);
			batchResult.add(response);
		}
		logger.info("Batch complete task instances end, and responses is: " + ObjectUtils.getDisplayString(batchResult));
		
		return batchResult.toArray(new RemoteWorkflowResponse[batchResult.size()]);
	}

}
