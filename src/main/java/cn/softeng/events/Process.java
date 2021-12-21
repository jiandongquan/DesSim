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
 * Process is a subclass of Thread that can be managed by the discrete event
 * simulation.
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
 *
 * Process 是Thread的子类，可以被离散事件仿真器进行管理
 * Process是startProcess和离散事件模型所需的所有功能的基础。每个进程创建自己运行的线程。这些线程由eventManager管理，当一个进程完成运行时，将被池化以便重用。
 * lock:进程中的所有状态必须在一个同步块内更新，使用进程本身作为锁对象。 在持有Process的锁时，一定要小心，千万不要使用eventManager的锁，因为这会导致其他线程试图从threadPool中唤醒你，从而导致死锁。
 *
 */
final class Process extends Thread {
    // Properties required to manage the pool of available Processes

    private static final ArrayList<Process> pool; // Paul: 所有Process的池，持有Process实例；
    private static final int maxPoolSize = 100; // Paul: 池的最多Process数量
    private static int numProcesses = 0; // Paul：已经生产的Process数量，同时用于为Process进行命名，

    private EventManager eventManager; // Paul：管理当前Processs的EventManager
    /**
     * The Process from which the present process was created
     * This is related to how the startProcess() API is implemented,
     * as sub-processes are started, the original process is paused until the subprocess either waits, or exists.
     * Upon waiting or exiting, the subprocess notifies whoever started it to continue execution,
     * the nextProcess filed is used to hold this reference.
     *  Paul: 创建当前Process的Process
     *  这与startProcess() API的实现方式有关，当子进程启动时，父进程将被暂停，直到子进程等待或退出。
     *  子进程在等待或退出时，通知启动它的父进程继续执行，nextProcess字段用于保存此引用。
     */
    private Process nextProcess;

    /**
     * The entity whose method is to be executed
     * Paul：要执行方法的实体
     */
    private ProcessTarget target;

    /**
     * These are a very special references that is only safe to use from the currently executing Process,
     * they are essentially Thread local variables that are only valid when activeFlag == true
     * Paul：这是一个非常特殊的引用，只有在当前执行的Process中使用才安全，它们本质上是线程局部变量，只有当activeFlag == true时才有效
     */
    private EventManager evt;

    /**
     * true if has nextProcess
     * Paul ：存在nextProcess时为True
     */
    private boolean hasNext;

    /**
     * Paul：死亡标记
     */
    private boolean dieFlag;

    /**
     * true if Process is active
     * Paul：当Process为活动时为true
     */
    private boolean activeFlag;

    static {
        // Initialize the storage for the pooled Processes
        // Paul :初始化process池；
        pool = new ArrayList<>(maxPoolSize);
    }

    /**
     * the Process contstructor only provide name for Process,
     * the state and content of process is provide by setup(),
     * because these attribute are constantly changing
     * @param name
     *
     * Paul：Process的构造器只提供Process的名称；
     * Process的状态与内容通过setup()方法提供，因为这些属性是不断变化的；
     */
    private Process(String name) {
        super(name);
    }

    /**
     * Returns a reference to the currently executing Process object
     * @return the currently executing process
     *
     * Paul：返回当前执行中的Process对象；
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
     * A process loops endlessly after it is created executing the method on the
     * target set as the entry point.  After completion, it calls endProcess and
     * will return it to a process pool if space is available, otherwise the resources
     * including the backing thread will be released.
     *
     * This method is called by Process.getProcess()
     *
     * Paul：
     * Run方法使用给定的参数调用Targer上的方法。在创建Process之后，在Target上执行作为入口点的方法，流程将无休止地循环。
     * 完成后，Process将调用endProcess，如果池空间可用，则将其返回到Process池，否则包括后台线程在内的资源将被释放。
     */
    @Override
    public void run() {
        while (true) {
            // wait in pool. why? -> look Process.getProcess()
            // the internal of getProcess() calls Process.start(),
            // starts the thread, pauses in waitInPool(), and
            // then puts it in the pool for use
            waitInPool();

            // Process has been woken up, execute the method we have been assigned
            // Process 被唤醒，开始执行分配的Target方法。
            ProcessTarget t;
            synchronized (this) {
                evt = eventManager;
                t = target;
                target = null;
                activeFlag = true;
                hasNext = (nextProcess != null);
            }

            evt.execute(this, t);

            // Ensure all state is cleared before returning to the pool
            // 在将Process交还池之前(通过setup())清除所有状态；
            evt = null;
            hasNext = false;
            setup(null, null, null);
        }
    }

    final boolean hasNext() {
        return hasNext;
    }

    final EventManager evt() {
        return evt;
    }

    /**
     * Useful to filter pooled threads when staring at stack traces.
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
                while (true) { pool.wait(); }
            } catch (InterruptedException e) {}
        }
    }

    /**
     * Setup the process state for execution.
     * Includes eventmanger, this process's waiter(caller) and target for execution
     * @param evt
     * @param next
     * @param targ
     *
     * Paul: 设置Process状态，用于初始化一个Process或清空Process到初始状态；
     */
    private synchronized void setup(EventManager evt, Process next, ProcessTarget targ) {
        eventManager = evt;
        nextProcess = next;
        target = targ;
        activeFlag = false;
        dieFlag = false;
    }

    /**
     * Pull a process from the pool and have it attempt to execute events from the given eventManager
     * @param evt
     *
     * Paul：处理事件，从Process池中获取一个Process，设置Process的EventManager后，唤醒Process进行工作；
     *
     */
    static void processEvents(EventManager evt) {
        Process newProcess = Process.getProcess();
        newProcess.setup(evt, null, null);
        newProcess.wake();
    }

    /**
     * Set up a new process for the given entity, method, and arguments
     * Called by EventManager.start() and EventManager.interruptEvent()
     * @param eventManager
     * @param next
     * @param target
     * @return a new process watiting to be awakened
     *
     * Paul：获取一个新的Process，设置Process的父Process，并返回此Process
     */
    static Process allocate(EventManager eventManager, Process next, ProcessTarget target) {
        Process newProcess = Process.getProcess();
        newProcess.setup(eventManager, next, target);
        return newProcess;
    }

    /**
     * Return a process from the pool or create a new one
     * @return a process which state is cleared or null, wait for setup and wake up
     *
     * Paul：从Process池中获取一个Process或新建一个Process
     */
    private static Process getProcess() {
        while (true) {
            synchronized (pool) {
                // If there is an available process in the pool, then use it
                // 如果Process池中存在可用Process，则返回Process
                if (pool.size() > 0) {
                    return pool.remove(pool.size() - 1);
                }
                // If there are no process in the pool, then create a new one and add it to the pool
                // 如果Process池中没有可用Process,则新建一个process，并加入到Process池中。
                else {
                    numProcesses++;
                    Process temp = new Process("processthread-" + numProcesses);
                    temp.start();
                    // Note: Thread.start() calls Process.run which adds the new process to the pool
                    // then continue the while loop to return created process
                    // Paul: temp.start() 调用Process.run()，Process.run()会将temp加入到Process池中；
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
     *
     * Paul： Threa的Interrupt()被覆盖，用于防止用户代码中断了事件状态机：如果用户代码显式地中断了一个进程，它可能会比预期更早地运行事件代码
     */
    @Override
    public void interrupt() {
        new Throwable("AUDIT: direct call of Process.interrupt").printStackTrace();
    }

    /**
     * This is the wrapper to allow internal code to advance the state machine by waking
     * a Process.
     *
     * Paul：这是一个包装器，允许内部代码通过唤醒一个Process以便推进状态机
     */
    final void wake() {
        super.interrupt();
    }


    synchronized void setNextProcess(Process next) {
        nextProcess = next;
    }

    /**
     * we woke a next Process,then set nextProcess field null
     * Paul:唤醒（中断）Process的子Process；
     */
    synchronized final void wakeNextProcess() {
        nextProcess.wake();
        nextProcess = null;
        hasNext = false;
    }

    // Paul: 杀死Process
    synchronized void kill() {
        if (activeFlag)
            throw new ProcessError("Cannot terminate an active thread");
        dieFlag = true;
        this.wake();
    }

    /**
     * This is used to tear down a live threadstack when an error is received from
     * the model.
     * paul:当从模型接收到错误时，用于杀死子Process；
     */
    synchronized Process forceKillNext() {
        Process ret = nextProcess;
        nextProcess = null;
        if (ret != null) {
            ret.dieFlag = true;
            ret.wake();
        }
        return ret;
    }

    synchronized boolean shouldDie() {
        return dieFlag;
    }

    /**
     * Preparation before the capture process, called by EventManager.captureProcess()
     * return nextProcess field, clear the nextProcess and hasNext state
     * @return nextProcess
     */
    synchronized final Process preCapture() {
        activeFlag = false;
        Process ret = nextProcess;
        nextProcess = null;
        hasNext = false;
        return ret;
    }

    /**
     * The action after capture process
     * set current process active, and update hasNext
     */
    synchronized final void postCapture() {
        activeFlag = true;
        hasNext = (nextProcess != null);
    }
}