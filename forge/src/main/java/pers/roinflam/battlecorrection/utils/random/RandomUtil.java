// 文件：RandomUtil.java
// 路径：src/main/java/pers/roinflam/battlecorrection/utils/random/RandomUtil.java
package pers.roinflam.battlecorrection.utils.random;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 随机工具类
 */
public class RandomUtil {

    private static final Random RANDOM = new Random();

    /**
     * 获取指定范围内的随机整数（包含min和max）
     *
     * @param min 最小值
     * @param max 最大值
     * @return 随机整数
     */
    public static int getInt(int min, int max) {
        if (min >= max) {
            return min;
        }
        return min + RANDOM.nextInt(max - min + 1);
    }

    /**
     * 按百分比概率返回true
     *
     * @param probability 概率（0-100）
     * @return 是否命中
     */
    public static boolean percentageChance(double probability) {
        return percentageChance(probability, 8);
    }

    /**
     * 按百分比概率返回true（高精度版本）
     *
     * @param probability 概率（0-100）
     * @param range       精度范围
     * @return 是否命中
     */
    private static boolean percentageChance(double probability, int range) {
        int multiplier = (int) Math.pow(10, range - 1);
        return getInt(0, 100 * multiplier) < (int) (probability * multiplier);
    }

    /**
     * 将总数随机分配到指定数量的列表中
     *
     * @param totalNumber 总数
     * @param count       分配数量
     * @return 分配结果列表
     */
    @Nonnull
    public static List<Integer> randomList(int totalNumber, int count) {
        List<Integer> list = new ArrayList<>();
        int leftNumber = totalNumber;
        int leftCount = count;

        for (int i = 0; i < count - 1; i++) {
            int number;
            if (leftNumber > 0) {
                if (leftNumber / leftCount * 2 < 1) {
                    number = leftNumber;
                } else {
                    number = 1 + RANDOM.nextInt(leftNumber / leftCount * 2);
                }
            } else {
                number = 0;
            }
            list.add(number);
            if (number > 0) {
                leftNumber -= number;
                leftCount--;
            }
        }
        list.add(leftNumber);
        return list;
    }
}