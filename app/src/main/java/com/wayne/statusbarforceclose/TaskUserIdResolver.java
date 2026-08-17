package com.wayne.statusbarforceclose;

import java.lang.reflect.Field;

final class TaskUserIdResolver {
    private static final String USER_ID_FIELD = "userId";

    private TaskUserIdResolver() {
    }

    static int resolve(Object task) {
        if (task == null) {
            return -1;
        }

        Class<?> taskClass = task.getClass();
        while (taskClass != null) {
            try {
                Field userIdField = taskClass.getDeclaredField(USER_ID_FIELD);
                userIdField.setAccessible(true);
                Object value = userIdField.get(task);
                return value instanceof Integer && (Integer) value >= 0
                        ? (Integer) value
                        : -1;
            } catch (NoSuchFieldException ignored) {
                taskClass = taskClass.getSuperclass();
            } catch (IllegalAccessException | RuntimeException ignored) {
                return -1;
            }
        }
        return -1;
    }
}
