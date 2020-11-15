/*
 * JaamSim Discrete Event Simulation
 * Copyright (C) 2002-2014 Ausenco Engineering Canada Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.softeng.events;

import java.util.ArrayList;

/**
 * Process is a subclass of Thread that can be managed by the discrete event simulation.
 *
 * This is the basis for all functionality required by startProcess and the
 * discrete event model. Each process creates its own thread to run in. These
 * threads are managed by the eventManager and when a Process has completed
 * running is pooled for reuse.
 *
 * LOCKING: All state in the Process must be updated from a synchronized block
 * using the Process itself as the lock object. Care must be taken to never take
 * the eventManager's lock while holding the Process's lock as this can cause a
 * deadlock with other threads trying to wake you from the threadPool.
 */
final class Process extends Thread {

    /**
     * storage for all available Processes
     */
    private static final ArrayList<Process> pool;

    /**
     * Maximum size of Process pool
     */
    private static final int maxPoolSize = 100;

    /**
     * Total number of created processes (to name a new Process)
     */
    private static int numProcesses = 0;

    /**
     * The EventManager that is currently managing this Process
     */
    private EventManager eventManager;

    /**
     * The Process from which the present process was created
     *
     * This is related to how the startProcess() API is implemented,
     * as sub-processes are started, the original process is paused until the subprocess either waits, or exists.
     * Upon waiting or exiting, the subprocess notifies whoever started it to continue execution,
     * the nextProcess filed is used to hold this reference.
     */
    private Process nextProcess;

    /**
     * The entity whose method is to be executed
     */
    private ProcessTarget target;

    /**
     * These are a very special references that is only safe to use from the currently executing Process,
     * they are essentially Thread local variables that are only valid when activeFlag == true
     */
    private EventManager evt;

    /**
     * true if has nextProcess
     */
    private boolean hasNext;

    private boolean dieFlag;
    /**
     * true if Process is active
     */
    private boolean activeFlag;

    private boolean condWait;

    static {
        // Initialize Process pool
        pool = new ArrayList<>(maxPoolSize);
    }

    /**
     * the Process contstructor only provide name for Process,
     * the state and content of process is provide by setup(),
     * because these attribute are constantly changing
     * @param name
     */
    private Process(String name) {
        super(name);
    }

    /**
     * Returns a reference to the currently executing Process object
     *
     * @return the currently executing process
     */
    static final Process current() {
        try {
            return (Process)Thread.currentThread();
        } catch (ClassCastException e) {
            throw new ProcessError("Non-process thread called Process.current()");
        }
    }

    /**
     * Run method invokes the method on the target with the given arguments.
     * A process loops endlessly after it is created, executing the method on the
     * target set as the entry point.  After completion, it calls endProcess and
     * will return it to a process pool if space is available, otherwise the resources
     * including the backing thread will be released.
     *
     * This method is called by Process.getProcess()
     */
    @Override
    public void run() {
        while (true) {
            // wait in pool
            waitInPool();

            // Process has been woken up, execute the method we have been assigned
            ProcessTarget t;
            synchronized (this) {
                activeFlag = true;
                evt = eventManager;
                t = target;
                target = null;
                hasNext = (nextProcess != null);
            }

            evt.execute(this, t);

            // Ensure all state is cleared before returning to the pool
            evt = null;
            hasNext = false;
            // 设置该线程的事件管理器，调用它的线程，和执行目标都为空
            setup(null, null, null);
        }
    }

    final boolean hasNext() {
        return hasNext;
    }

    final EventManager evt() {
        return evt;
    }

    // Useful to filter pooled threads when staring at stack traces.

    /**
     *
     */
    private void waitInPool() {
        synchronized (pool) {
            // Add ourselves to the pool and wait to be assigned work
            pool.add(this);
            // Set the present process to sleep, and release its lock
            // (done by pool.wait();)
            // Note: the try/while(true)/catch construct is needed to avoid
            // spurious wake ups allowed as of Java 5.  All legitimate wake
            // ups are done through the InterruptedException.
            try {
                while (true) {
                    pool.wait();
                }
            } catch (InterruptedException e) {}
        }
    }


    /**
     * Setup the process state for execution.
     * Includes eventmanger, this process's waiter(caller) and target for execution
     * @param evt
     * @param next
     * @param targ
     */
    private synchronized void setup(EventManager evt, Process next, ProcessTarget targ) {
        eventManager = evt;
        nextProcess = next;
        target = targ;
        activeFlag = false;
        dieFlag = false;
        condWait = false;
    }


    /**
     * 从线程池拉取一个线程, 尝试执行给定eventManager中的事件
     * Pull a process from the pool and have it attempt to execute events from the given eventManager
     * @param evt
     */
    static void processEvents(EventManager evt) {
        Process newProcess = Process.getProcess();
        newProcess.setup(evt, null, null);
        newProcess.wake();
    }

    // Set up a new process for the given entity, method, and arguments
    // Called from Process.start() and from EventManager.startExternalProcess()
    static Process allocate(EventManager eventManager, Process next, ProcessTarget proc) {
        Process newProcess = Process.getProcess();
        newProcess.setup(eventManager, next, proc);
        return newProcess;
    }

    // Return a process from the pool or create a new one
    private static Process getProcess() {
        while (true) {
            synchronized (pool) {
                // If there is an available process in the pool, then use it
                if (pool.size() > 0) {
                    return pool.remove(pool.size() - 1);
                }
                // If there are no process in the pool, then create a new one and add it to the pool
                else {
                    numProcesses++;
                    Process temp = new Process("processthread-" + numProcesses);
                    temp.start(); // Note: Thread.start() calls Process.run which adds the new process to the pool
                }
            }

            // Allow the Process.run method to execute so that it can add the
            // new process to the pool
            // Note: that the lock on the pool has been dropped, so that the
            // Process.run method can grab it.
            try { Thread.sleep(10); } catch (InterruptedException e) {}
        }
    }

    /**
     * We override this method to prevent user code from breaking the event state machine.
     * If user code explicitly interrupted a Process it would likely run event code
     * much earlier than intended.
     */
    @Override
    public void interrupt() {
        new Throwable("AUDIT: direct call of Process.interrupt").printStackTrace();
    }

    /**
     * This is the wrapper to allow internal code to advance the state machine by waking
     * a Process.
     *
     */
    final void wake() {
        super.interrupt();
    }

    synchronized void setNextProcess(Process next) {
        nextProcess = next;
    }

    /**
     * Returns true if we woke a next Process, otherwise return false.
     */
    synchronized final void wakeNextProcess() {
        nextProcess.wake();
        nextProcess = null;
        hasNext = false;
    }

    synchronized void kill() {
        if (activeFlag)
            throw new ProcessError("Cannot terminate an active thread");
        dieFlag = true;
        this.wake();
    }

    /**
     * This is used to tear down a live threadstack when an error is received from
     * the model.
     */
    synchronized Process forceKillNext() {
        Process ret = nextProcess;
        nextProcess = null;
        if (ret != null) {
            // 将线程的关闭flag设置为真
            ret.dieFlag = true;
            // 唤醒线程，线程被唤醒后会检查 cur.shouldDie()，从而抛出异常
            ret.wake();
        }
        return ret;
    }

    synchronized boolean shouldDie() {
        return dieFlag;
    }

    synchronized final Process preCapture() {
        activeFlag = false;
        Process ret = nextProcess;
        nextProcess = null;
        hasNext = false;
        return ret;
    }

    synchronized final void postCapture() {
        activeFlag = true;
        hasNext = (nextProcess != null);
    }

    final void begCondWait() {
        condWait = true;
    }

    final void endCondWait() {
        condWait = false;
    }

    final void checkCondWait() {
        if (condWait) {
            throw new ProcessError("Event Control attempted from inside a Conditional callback");
        }
    }
}
