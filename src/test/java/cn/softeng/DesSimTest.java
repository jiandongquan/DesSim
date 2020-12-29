package cn.softeng;

import cn.softeng.processflow.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.util.Map;

/**
 * Unit test for simple DesSim.
 */
@Slf4j
public class DesSimTest
{
    /**
     * Rigorous Test :-)
     */
    @Test
    public void testSerialScheduling() throws InterruptedException {
        EntityLauncher launcher = new EntityLauncher();
        launcher.setName("launcher");
        Queue queue1 = new Queue();
        queue1.setName("queue1");
        Queue queue2 = new Queue();
        queue2.setName("queue2");
        Server server1 = new Server();
        server1.setName("server1");
        Server server2 = new Server();
        server2.setName("server2");
        EntitySink sink = new EntitySink();
        sink.setName("sink");

        launcher.setNextComponent(queue1);
        server1.setWaitQueue(queue1);
        server1.setServiceTime(3);
        server1.setNextComponent(queue2);
        server2.setWaitQueue(queue2);
        server2.setServiceTime(3);
        server2.setNextComponent(sink);

        DesSim.initModel(DesSim.Type.HORIZONTAL);
        DesSim.serialScheduling(0, 1);


        log.debug("{}", server1.getName());
        log.debug("getNumAddMap");
        for (Map.Entry<Long, Long> entry : server1.getNumAddMap().entrySet()) {
            log.debug("{} - {}", entry.getKey(), entry.getValue());
        }
        log.debug("getNumInProgress");
        for (Map.Entry<Long, Long> entry : server1.getNumInProgress().entrySet()) {
            log.debug("{} - {}", entry.getKey(), entry.getValue());
        }
        log.debug(server1.getName());
        log.debug("getNumProcessed");
        for (Map.Entry<Long, Long> entry : server1.getNumProcessedMap().entrySet()) {
            log.debug("{} - {}", entry.getKey(), entry.getValue());
        }

        while (true) {
            Thread.sleep(1);
        }
    }

    @Test
    public void testParallelScheduling() throws InterruptedException {
        EntityLauncher launcher = new EntityLauncher();
        launcher.setName("launcher");
        Queue queue1 = new Queue();
        queue1.setName("queue1");
        Queue queue2 = new Queue();
        queue2.setName("queue2");
        Server server1 = new Server();
        server1.setName("server1");
        Server server2 = new Server();
        server2.setName("server2");
        EntitySink sink = new EntitySink();
        sink.setName("sink");

        launcher.setNextComponent(queue1);
        server1.setWaitQueue(queue1);
        server1.setServiceTime(2);
        server1.setNextComponent(queue2);
        server2.setWaitQueue(queue2);
        server2.setServiceTime(3);
        server2.setNextComponent(sink);

        DesSim.initModel(DesSim.Type.VERTICAL);

        DesSim.parallelScheduling(5, 1);
        DesSim.resume(5);
        DesSim.resume(7);
        log.debug("{}", DesSim.hasEvent() ? "has Event" : "no Event");

        DesSim.parallelScheduling(7, 1);
        DesSim.resume(10);
        DesSim.resume(100);
        DesSim.parallelScheduling(100, 1);
        DesSim.resume(200);
        log.debug("{}", DesSim.hasEvent() ? "has Event" : "no Event");


        log.debug("{}", server1.getName());
        log.debug("getNumAddMap");
        for (Map.Entry<Long, Long> entry : server1.getNumAddMap().entrySet()) {
            log.debug("{} - {}", entry.getKey(), entry.getValue());
        }
        log.debug("getNumInProgress");
        for (Map.Entry<Long, Long> entry : server1.getNumInProgress().entrySet()) {
            log.debug("{} - {}", entry.getKey(), entry.getValue());
        }
        log.debug(server1.getName());
        log.debug("getNumProcessed");
        for (Map.Entry<Long, Long> entry : server1.getNumProcessedMap().entrySet()) {
            log.debug("{} - {}", entry.getKey(), entry.getValue());
        }

        // Junit本身是不支持普通的多线程测试的，这是因为Junit的底层实现上，是用System.exit退出用例执行的。
        // JVM终止了，在测试线程启动的其他线程自然也无法执行。所以手动睡眠主线程。
        while (true) {
            Thread.sleep(1);
        }
    }

}
