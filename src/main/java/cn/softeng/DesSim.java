package cn.softeng;

import cn.softeng.basicsim.Entity;
import cn.softeng.basicsim.InitModelTarget;
import cn.softeng.events.EventManager;
import cn.softeng.processflow.*;
import lombok.extern.slf4j.Slf4j;

import java.security.InvalidParameterException;
import java.util.*;

/**
 * DES 对外调度接口
 */
@Slf4j
public class DesSim {

    /**
     * 负责调度DES的事件管理器
     */
    private static EventManager eventManager = new EventManager("DesSim");

    /**
     * Des 实体的生成状态
     */
    private static Type desType;

    /**
     * 添加到组件的实体数量
     */
    public static final String NumberAdded = "NumberAdded";

    /**
     * 已经处理完毕的实体数量
     */
    public static final String NumberProcessed = "NumberProcessed";

    /**
     * 正在处理中的实体数量
     */
    public static final String NumberInProgress = "NumberInProgress";

    /**
     * 初始化模型，确认DES类型
     *
     */
    public static void initModel(Type type) {
        desType = type;
        // 清空时间管理的状态
        eventManager.clear();
        // 向事件队列中添加初始化模型的事件
        eventManager.scheduleProcessExternal(0, 0, false, new InitModelTarget(), null);
        // 执行0时刻的初始化操作
        resume(0);
    }

    /**
     * 水平模式下：注入后会立即执行
     * 垂直模式下：出入后不会立即执行，需要手动触发
     * @param scheduleTime
     * @param num
     */
    public static void inject(int scheduleTime, int num) {
        if (desType == Type.Generator) {
            throw new RuntimeException("自动生成实体模式下，不支持 inject !!!");
        }
        parallelScheduling(scheduleTime, num);
    }

    /**
     * DES被并行的调度，调用时必须确保DES调度正在运行，否则会报错
     * @param scheduleTime 时间调度时间
     * @param num 注入个数
     */
    public static void parallelScheduling(int scheduleTime, int num) {
        for (Entity entity : Entity.getAll()) {
            if (entity.getClass() == EntityLauncher.class) {
                EntityLauncher launcher = (EntityLauncher) entity;
                launcher.scheduleAction(eventManager, scheduleTime, num);
                break;
            }
        }
    }

    /**
     * 执行事件直到指定时刻
     * @param time
     */
    public static void resume(double time) {
        eventManager.resume(time);
        while (eventManager.isRunning()) {
            try { Thread.sleep(1); } catch (InterruptedException e) { e.printStackTrace(); }
        }
    }

    /**
     * 执行事件直到指定时刻
     * @param time
     */
    public static void doEvent(long time) {
        resume(time);
    }

    /**
     * 获取模型的时钟序列
     * @return
     */
    public static List<Double> getTimePointList() {
        return new ArrayList<>(eventManager.getTimePointSet());
    }

    /**
     * 通id获取对应的组件
     * @param id
     * @return
     */
    public static LinkedComponent getEntity(int id) {
        return getEntity(String.valueOf(id));
    }

    /**
     * 更具实体表示获取实体
     * @param identifier 实体表示
     * @return
     */
    public static LinkedComponent getEntity(String identifier) {
        Entity ret = Entity.getNamedEntity(identifier);
        return (LinkedComponent) ret;
    }

    /**
     * 事件队列中即将执行的事件的时间
     * @return
     */
    public static double nextEventTime() {
        return eventManager.getNextEventTime();
    }

    /**
     * DES系统当前仿真时间
     * @return
     */
    public static double currentSimTime() {
        return eventManager.getCurrentTime();
    }

    /**
     * 事件队列中是否有事件
     * @return
     */
    public static boolean hasEvent() {
        return eventManager.hasEvent();
    }

    /**
     * 获取指定组件的特定属性
     * @param identifier 组件的标识符
     * @param attr 属性
     * @return
     */
    public static long getCurrentData(int identifier, String attr) {
        LinkedComponent linkedComponent =  getEntity(identifier);
        if (attr.equals(NumberAdded)) {
            return linkedComponent.getNumberAdded();
        } else if (attr.equals(NumberInProgress)) {
            return linkedComponent.getNumberInProgress();
        } else if (attr.equals(NumberProcessed)) {
            return linkedComponent.getNumberProcessed();
        }
        throw new InvalidParameterException("attr 不存在");
    }

    /**
     * 选定指定组件，指定属性到目前为止的所有数据
     * @param identifier
     * @param attr
     * @return
     */
    public static List<Long> getDataList(int identifier, String attr) {
        return getDataList(String.valueOf(identifier), attr);
    }

    /**
     * 选定指定组件，指定属性到目前为止的所有数据
     * @param identifier
     * @param attr
     * @return
     */
    public static List<Long> getDataList(String identifier, String attr) {
        LinkedComponent linkedComponent =  getEntity(identifier);
        if (attr.equals(NumberAdded)) {
            return linkedComponent.getNumAddList();
        } else if (attr.equals(NumberInProgress)) {
            return linkedComponent.getNumInProgressList();
        } else if (attr.equals(NumberProcessed)) {
            return linkedComponent.getNumProcessedList();
        }
        throw new InvalidParameterException("attr 不存在");
    }

    /**
     * 选定指定组件，指定属性到目前为止的所有数据
     * @param identifier
     * @param attr
     * @return
     */
    public static Vector<Long> getDataList(int identifier, String attr) {
        LinkedComponent linkedComponent =  getEntity(identifier);
        if (attr.equals(NumberAdded)) {
            return linkedComponent.getNumAddList();
        } else if (attr.equals(NumberInProgress)) {
            return linkedComponent.getNumInProgressList();
        } else if (attr.equals(NumberProcessed)) {
            return linkedComponent.getNumProcessedList();
        }
        throw new InvalidParameterException("attr 不存在");
    }

    public static void main(String[] args) {
        System.out.println("Hello ! this is DesSim");
    }

    public enum Type {
        /**
         * DES 自己定时生成实体
         */
        Generator,
        /**
         * DES 被触发后才生成实体
         */
        Launcher,
    }

    public static void main(String[] args) {
        System.out.println("Hello ! this is DesSim");
    }

}
