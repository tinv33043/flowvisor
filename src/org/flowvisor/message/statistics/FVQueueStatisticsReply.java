package org.flowvisor.message.statistics;

import java.util.Iterator;
import java.util.List;

import org.flowvisor.classifier.FVClassifier;
import org.flowvisor.flows.FlowIntersect;
import org.flowvisor.flows.SliceAction;
import org.flowvisor.log.FVLog;
import org.flowvisor.log.LogLevel;
import org.flowvisor.message.FVMessageUtil;
import org.flowvisor.message.FVStatisticsReply;
import org.flowvisor.message.FVStatisticsRequest;
import org.flowvisor.openflow.protocol.FVMatch;
import org.flowvisor.slicer.FVSlicer;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.statistics.OFQueueStatisticsReply;
import org.openflow.protocol.statistics.OFStatistics;

public class FVQueueStatisticsReply extends OFQueueStatisticsReply implements
		ClassifiableStatistic, SlicableStatistic {


	
    @Override
    public void sliceFromController(FVStatisticsRequest msg, FVClassifier fvClassifier,
                    FVSlicer fvSlicer) {
            FVLog.log(LogLevel.WARN, fvSlicer, "dropping unexpected msg: " + msg);
    }

    /**
     * No need to rewrite response
     */

    @Override
    public void classifyFromSwitch(FVStatisticsReply msg, FVClassifier fvClassifier) {
    	FVSlicer fvSlicer = FVMessageUtil.untranslateXid(msg, fvClassifier);
    	if (fvSlicer == null) {
    		FVLog.log(LogLevel.WARN, fvClassifier,
    				"dropping unclassifiable port stats reply: " + this);
    		return;
    	}

    	Iterator<OFStatistics> it = msg.getStatistics().iterator();
    	while (it.hasNext()) {
    		FVQueueStatisticsReply reply = (FVQueueStatisticsReply) it.next();
    		if (!fvSlicer.portInSlice(reply.portNumber)) {
    			FVLog.log(LogLevel.WARN, fvClassifier, "Port " + reply.portNumber + 
    					" is not in slice " + fvSlicer.getSliceName());
    			it.remove();
    			msg.setLengthU(msg.getLengthU() - reply.computeLength());
    			continue;
    		}
    		FVMatch testMatch = new FVMatch();
    		testMatch.setInputPort(reply.portNumber);
    		List<FlowIntersect> intersections = 
    				fvSlicer.getFlowSpace().intersects(fvClassifier.getDPID(), testMatch);
    		boolean found = false;
    		for (FlowIntersect inter : intersections) {
    			if (inter.getFlowEntry().getRuleMatch().getQueues().contains(reply.queueId)) {
    				for (OFAction act : inter.getFlowEntry().getActionsList()) {
    					assert(act instanceof SliceAction);
    					SliceAction sa = (SliceAction) act;
    					if (sa.getSliceName().equals(fvSlicer.getSliceName())) {
    						found = true;
    						break;
    					}
    				}
    				if (found)
    					break;
    			} 
    		}
    		if (!found) {
    			it.remove();
    			msg.setLengthU(msg.getLengthU() - reply.computeLength());
    			FVLog.log(LogLevel.WARN, fvClassifier, "QueueId " + reply.queueId + 
						" is not associtated to port " + reply.getPortNumber() + 
						" in slice " + fvSlicer.getSliceName());
    		}
    	}
    	if (msg.getStatistics().size() > 0) {
    		fvSlicer.sendMsg(msg, fvClassifier);
    	} else {
    		FVLog.log(LogLevel.WARN, fvClassifier, "Dropping emptied Queue stats reply: ", msg);
    	}

    }


}
