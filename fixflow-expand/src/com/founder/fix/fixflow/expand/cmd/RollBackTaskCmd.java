package com.founder.fix.fixflow.expand.cmd;

import java.util.Set;

import com.founder.fix.fixflow.core.exception.FixFlowException;
import com.founder.fix.fixflow.core.factory.ProcessObjectFactory;
import com.founder.fix.fixflow.core.impl.bpmn.behavior.ProcessDefinitionBehavior;
import com.founder.fix.fixflow.core.impl.bpmn.behavior.TaskCommandInst;

import com.founder.fix.fixflow.core.impl.bpmn.behavior.UserTaskBehavior;
import com.founder.fix.fixflow.core.impl.cmd.AbstractExpandTaskCmd;
import com.founder.fix.fixflow.core.impl.expression.ExpressionMgmt;
import com.founder.fix.fixflow.core.impl.interceptor.CommandContext;
import com.founder.fix.fixflow.core.impl.persistence.ProcessDefinitionManager;
import com.founder.fix.fixflow.core.impl.persistence.ProcessInstanceManager;
import com.founder.fix.fixflow.core.impl.persistence.TaskManager;
import com.founder.fix.fixflow.core.impl.runtime.ProcessInstanceEntity;
import com.founder.fix.fixflow.core.impl.runtime.TokenEntity;
import com.founder.fix.fixflow.core.impl.task.TaskInstanceEntity;
import com.founder.fix.fixflow.core.runtime.ExecutionContext;
import com.founder.fix.fixflow.core.task.TaskInstance;
import com.founder.fix.fixflow.expand.command.RollBackTaskCommand;

public class RollBackTaskCmd extends AbstractExpandTaskCmd<RollBackTaskCommand,Void>{

	/**
	 * 退回到的节点
	 */
	protected String rollBackNodeId;
	
	public RollBackTaskCmd(RollBackTaskCommand rollBackTaskCommand) {
		super(rollBackTaskCommand);
		this.rollBackNodeId=rollBackTaskCommand.getRollBackNodeId();
	}

	public Void execute(CommandContext commandContext) {
		if (taskId == null||taskId.equals("")) {
			throw new FixFlowException("任务编号为空！");
		}

		TaskManager taskManager = commandContext.getTaskManager();

		TaskInstance taskInstanceQuery = taskManager.findTaskById(taskId);

		String tokenId = taskInstanceQuery.getTokenId();
		String nodeId = taskInstanceQuery.getNodeId();
		String processDefinitionId = taskInstanceQuery.getProcessDefinitionId();
		ProcessInstanceManager processInstanceManager = commandContext.getProcessInstanceManager();

		String processInstanceId = taskInstanceQuery.getProcessInstanceId();

		ProcessDefinitionManager processDefinitionManager = commandContext.getProcessDefinitionManager();

		ProcessDefinitionBehavior processDefinition = processDefinitionManager.findLatestProcessDefinitionById(processDefinitionId);

		UserTaskBehavior userTask = (UserTaskBehavior) processDefinition.getDefinitions().getElement(nodeId);

		
		
		TaskCommandInst taskCommand=null;
		
		if(this.admin!=null&&!this.admin.equals("")){
			
			String taskCommandName=commandContext.getProcessEngineConfigurationImpl().getTaskCommandDefMap().get(userCommandId).getName();
			
			taskCommand=new TaskCommandInst(userCommandId, taskCommandName, null, userCommandId, true);
			
			
		}
		else{
			taskCommand = userTask.getTaskCommandsMap().get(userCommandId);
		}
		
		ProcessInstanceEntity processInstanceImpl = processInstanceManager.findProcessInstanceById(processInstanceId, processDefinition);

		

		processInstanceImpl.getContextInstance().setVariableMap(variables);
		

		TokenEntity token = processInstanceImpl.getTokenMap().get(tokenId);
		
		processInstanceImpl.getContextInstance().setTransientVariableMap(transientVariables);
		processInstanceImpl.getContextInstance().setVariableMap(variables);
		

		ExecutionContext executionContext = ProcessObjectFactory.FACTORYINSTANCE.createExecutionContext(token);
		
		if (taskCommand != null && taskCommand.getExpression() != null) {
			try {
				
				ExpressionMgmt.execute(taskCommand.getExpression(), executionContext);
			} catch (Exception e) {
				throw new FixFlowException("用户命令表达式执行异常!", e);
			}
		}

		if (taskCommand != null) {
			
			
			


			Set<TaskInstanceEntity> taskInstances = processInstanceImpl.getTaskMgmtInstance().getTaskInstanceEntitys();
			TaskInstanceEntity taskInstanceImpl = null;
			for (TaskInstanceEntity taskInstance : taskInstances) {
				if (taskInstance.getId().equals(taskId)) {
					taskInstanceImpl = taskInstance;
				}
			}

			if (taskInstanceImpl == null) {
				throw new FixFlowException("需要退回的任务: " + taskId + " 不存在!");
			}
			try {

				
				UserTaskBehavior backNodeUserTask = (UserTaskBehavior) executionContext.getProcessDefinition().getDefinitions().getElement(rollBackNodeId);
				taskInstanceImpl.toFlowNodeEnd(taskCommand, taskComment, this.agent, this.admin, backNodeUserTask, null);

				


			} catch (Exception e) {

				throw new FixFlowException("任务: " + taskId + " 退回失败!", e);
			}


		}

		try {
			processInstanceManager.saveProcessInstance(processInstanceImpl);
		} catch (Exception e) {
			throw new FixFlowException("流程实例持久化失败!", e);
		}
		return null;
		
		
	}

}
