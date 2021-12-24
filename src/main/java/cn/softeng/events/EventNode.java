package cn.softeng.events;


// EventNode 事件节点。主要用于存放具有相同计划执行时刻与优先级别的事件；
class EventNode {

    interface Runner {
        void runOnNode(EventNode node);
    }

    // 事件的计划执行时刻
    long schedTick; // The tick at which this event will execute
    // 事件的优先级别
    int priority;   // The schedule priority of this event
    // 头部事件
    Event head;
    // 尾部事件；
    Event tail;

    // 红黑标记；
    boolean red;
    // 左边节点
    EventNode left;
    // 右边节点；
    EventNode right;

    // 构造函数
    // 创建时设置计划执行时刻，优先级别，左右节点都nil节点；
    EventNode(long tick, int prio) {
        schedTick = tick;
        priority = prio;
        left = nilNode;
        right = nilNode;
    }

    // 添加事件，会根据是否有先入先出要求，在Head或Tail后形成事件链；
    // 具体参考算法图解.drawio
    final void addEvent(Event e, boolean fifo) {
        // 如果当前节点没有头部事件，则记录当前节点的头部事件为E,尾部事件为E;E的下一个事件为null
        if (head == null) {

            head = e;
            tail = e;
            e.next = null;
            return;
        }
        // 如果存在先入先出要求，从Tail开始形成事件链
        if (fifo) {
            tail.next = e;
            tail = e;
            e.next = null;
        }
        // 没有先入先出要求，从head开始形成事件链；
        else {
            e.next = head;
            head = e;
        }
    }
    //删除事件，关键处理是在移除事件后把链接上
    final void removeEvent(Event evt) {
        // quick case where we are the head event
        //如果evt等于Head事件，
        if (this.head == evt) {
            this.head = evt.next;
            if (evt.next == null) {  // 并且Head事件后没有下一个事件，
                this.tail = null;
            }
        }
        else {
            Event prev = this.head;
            while (prev.next != evt) {
                prev = prev.next;
            }
            prev.next = evt.next;
            if (evt.next == null) {
                this.tail = prev;
            }
        }
    }

    // 通过计划执行时刻与优先级比较两个事件Node，先计划执行时刻，相同的话，则比较优先级；
    // 如果当前事件Node的计划执行时刻小（早），则返回-1；
    // 如果当前事件Node的计划执行时刻大（晚），则返回1；
    // 计划执行时刻相同，则当前事件Node优先级小，则返回-1；
    // 计划执行时刻相同，则当前事件Node优先级大，则返回1；

    final int compareToNode(EventNode other) {
        return compare(other.schedTick, other.priority);
    }

    final int compare(long schedTick, int priority) {
        if (this.schedTick < schedTick) {
            return -1;
        }
        if (this.schedTick > schedTick) {
            return  1;
        }

        if (this.priority < priority) {
            return -1;
        }
        if (this.priority > priority) {
            return  1;
        }
        return 0;
    }

    // 事件节点右旋
    final void rotateRight(EventNode parent) {
        if (parent != null) {
            if (parent.left == this) {
                parent.left = left;
            } else {
                parent.right = left;
            }
        }

        EventNode oldMid = left.right;
        left.right = this;

        this.left = oldMid;
    }

    // 事件节点左旋
    final void rotateLeft(EventNode parent) {
        if (parent != null) {
            if (parent.left == this) {
                parent.left = right;
            } else {
                parent.right = right;
            }
        }
        EventNode oldMid = right.left;
        right.left = this;
        this.right = oldMid;
    }

    //从指定事件节点克隆；
    final void cloneFrom(EventNode source) {
        this.head = source.head;
        this.tail = source.tail;
        this.schedTick = source.schedTick;
        this.priority = source.priority;
        Event next = this.head;
        while (next != null) {
            next.node = this;
            next = next.next;
        }
    }

    // nil节点，0时刻，0优先级，左右事件节点皆null;黑色标记；
    static final EventNode nilNode;

    static {
        nilNode = new EventNode(0, 0);
        nilNode.left = null;
        nilNode.right = null;
        nilNode.red = false;
    }
}