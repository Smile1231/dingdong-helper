import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 抢菜主程序
 */
public class Application {


    public static final Map<String, Map<String, Object>> map = new ConcurrentHashMap<>();


    public static void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
        }
    }


    public static void main(String[] args) {

        if (UserConfig.addressId.length() == 0) {
            System.err.println("请先执行UserConfig获取配送地址id");
            return;
        }

        //此为高峰期策略 通过同时获取或更新 购物车、配送、订单确认信息再进行高并发提交订单

        //一定要注意 并发量过高会导致被风控 请合理设置线程数、等待时间和执行时间 不要长时间的执行此程序（我配置的线程数和间隔 2分钟以内）
        //如果想等过高峰期后进行简陋 长时间执行 则将线程数改为1  间隔时间改为10秒以上 并发越小越像真人 不会被风控  要更真一点就用随机数（自己处理）
        
        //基础信息执行线程数
        int baseTheadSize = 2;

        //提交订单执行线程数
        int submitOrderTheadSize = 4;

        //请求间隔时间
        int sleepMillis = 300;

        for (int i = 0; i < baseTheadSize; i++) {
            new Thread(() -> {
                while (!map.containsKey("end")) {
                    Api.allCheck();
                    //此接口作为补充使用 并不是一定需要 所以执行间隔拉大一点
                    sleep(5000);
                }
            }).start();
        }

        for (int i = 0; i < baseTheadSize; i++) {
            new Thread(() -> {
                while (!map.containsKey("end")) {
                    Map<String, Object> cartMap = Api.getCart();
                    if (cartMap != null) {
                        map.put("cartMap", cartMap);
                    }
                    sleep(sleepMillis);
                }
            }).start();
        }
        for (int i = 0; i < baseTheadSize; i++) {
            new Thread(() -> {
                while (!map.containsKey("end")) {
                    sleep(sleepMillis);
                    if (map.get("cartMap") == null) {
                        continue;
                    }
                    Map<String, Object> multiReserveTimeMap = Api.getMultiReserveTime(UserConfig.addressId, map.get("cartMap"));
                    if (multiReserveTimeMap != null) {
                        map.put("multiReserveTimeMap", multiReserveTimeMap);
                    }
                }
            }).start();
        }
        for (int i = 0; i < baseTheadSize; i++) {
            new Thread(() -> {
                while (!map.containsKey("end")) {
                    sleep(sleepMillis);
                    if (map.get("cartMap") == null || map.get("multiReserveTimeMap") == null) {
                        continue;
                    }
                    Map<String, Object> checkOrderMap = Api.getCheckOrder(UserConfig.addressId, map.get("cartMap"), map.get("multiReserveTimeMap"));
                    if (checkOrderMap != null) {
                        map.put("checkOrderMap", checkOrderMap);
                    }
                }
            }).start();
        }
        for (int i = 0; i < submitOrderTheadSize; i++) {
            new Thread(() -> {
                while (!map.containsKey("end")) {
                    if (map.get("cartMap") == null || map.get("multiReserveTimeMap") == null || map.get("checkOrderMap") == null) {
                        sleep(sleepMillis);
                        continue;
                    }
                    Api.addNewOrder(UserConfig.addressId, map.get("cartMap"), map.get("multiReserveTimeMap"), map.get("checkOrderMap"));
                }
            }).start();
        }
    }
}
