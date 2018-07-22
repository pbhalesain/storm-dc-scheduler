package org.apache.storm.scheduler.dc;

import org.apache.storm.scheduler.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
/**
 * This scheduler makes sure a topology tagged with a preferred data center <code>DC1</code>
 * runs on a supervisor tagged with <code>DC1</code> data center. If there are no matching supervisors
 * available, the topology runs on availabe supervisors possibly in another data center.
 * The supervisors are configured using the config: <code>supervisor.scheduler.meta</code>.
 *
 * For example, we need to put the following config in supervisor's <code>storm.yaml</code>:
 * <pre>
 *     # give our supervisor a name: "special-supervisor"
 *     supervisor.scheduler.meta:
 *       dc_name: "DC1"
 * </pre>
 *
 * Put the following config in <code>nimbus</code>'s <code>storm.yaml</code>:
 * <pre>
 *     # tell nimbus to use this custom scheduler
 *     storm.scheduler: "org.apache.storm.scheduler.dc.PreferredDCScheduler"
 * </pre>
 * @author pbhalesain July 21, 2018 16:17:43 PM
 */

public class PreferredDCScheduler implements IScheduler {

    private final static Logger LOG = LoggerFactory.getLogger(PreferredDCScheduler.class);

    private static String SUPERVISOR_TAG_STRING  = "special-supervisor";
    private static String SPOUT_TAG_STRING       = "special-spout";
    private static String TOPOLOGY_TAG_STRING    = "special-topology";

    @Override
    public void prepare(Map map) { }

    @Override
    public void schedule(Topologies topologies, Cluster cluster) {
        System.out.println("PreferredDCScheduler: begin scheduling");

        TopologyDetails topology = topologies.getByName(TOPOLOGY_TAG_STRING);

        if (topology != null){
            boolean needsScheduling = cluster.needsScheduling(topology);
            if (!needsScheduling) {
                System.out.println("Our special topology DOES NOT NEED scheduling.");
            }
        }else {
            LOG.debug("Our special topology needs scheduling");

            Map<String, List<ExecutorDetails>> componentToExecutors =
                    cluster.getNeedsSchedulingComponentToExecutors(topology);
            System.out.println("needs scheduling(component->executor): " + componentToExecutors);
            System.out.println("needs scheduling(executor->compoenents): " + cluster.getNeedsSchedulingExecutorToComponents(topology));

            SchedulerAssignment currentAssignment = cluster.getAssignmentById(topologies.getByName("special-topology").getId());

            if (currentAssignment != null) {
                System.out.println("current assignments: " + currentAssignment.getExecutorToSlot());
            } else {
                System.out.println("current assignments: {}");
            }

            if (!componentToExecutors.containsKey(SPOUT_TAG_STRING)){
                System.out.println("Our special-spout DOES NOT NEED scheduling.");
            } else {
                System.out.println("Our special-spout needs scheduling.");
                List<ExecutorDetails> executors = componentToExecutors.get("special-spout");

                // find out the our "special-supervisor" from the supervisor metadata
                Collection<SupervisorDetails> supervisors = cluster.getSupervisors().values();
                SupervisorDetails specialSupervisor = null;
                for (SupervisorDetails supervisor : supervisors ){
                    Map meta = (Map) supervisor.getSchedulerMeta();

                    if (meta.get("name").equals(SUPERVISOR_TAG_STRING)) {
                        specialSupervisor = supervisor;
                        break;
                    }
                }

                // found the special supervisor
                if (specialSupervisor != null) {
                    System.out.println("Found the special-supervisor");
                    List<WorkerSlot> availableSlots = cluster.getAvailableSlots(specialSupervisor);

                    // if there is no available slots on this supervisor, free some.
                    // TODO for simplicity, we free all the used slots on the supervisor.
                    if (availableSlots.isEmpty() && !executors.isEmpty()) {
                        for (Integer port : cluster.getUsedPorts(specialSupervisor)) {
                            cluster.freeSlot(new WorkerSlot(specialSupervisor.getId(), port));
                        }
                    }

                    // re-get the aviableSlots
                    availableSlots = cluster.getAvailableSlots(specialSupervisor);

                    // since it is just a demo, to keep things simple, we assign all the
                    // executors into one slot.
                    cluster.assign(availableSlots.get(0), topology.getId(), executors);
                    System.out.println("We assigned executors:" + executors + " to slot: [" + availableSlots.get(0).getNodeId() + ", " + availableSlots.get(0).getPort() + "]");

                } else {
                    System.out.println("There is no supervisor named special-supervisor!!!");
                }

            }

        }
    }
}
