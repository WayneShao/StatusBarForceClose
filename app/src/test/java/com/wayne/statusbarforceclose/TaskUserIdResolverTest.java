package com.wayne.statusbarforceclose;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TaskUserIdResolverTest {
    @Test
    public void resolvesPrimaryUser() {
        assertEquals(0, TaskUserIdResolver.resolve(new DirectTask(0)));
    }

    @Test
    public void resolvesHyperOsDualAppUser() {
        assertEquals(999, TaskUserIdResolver.resolve(new DirectTask(999)));
    }

    @Test
    public void resolvesOtherSecondaryUser() {
        assertEquals(10, TaskUserIdResolver.resolve(new DirectTask(10)));
    }

    @Test
    public void findsUserIdOnSuperclass() {
        assertEquals(999, TaskUserIdResolver.resolve(new InheritedTask(999)));
    }

    @Test
    public void rejectsNullTask() {
        assertEquals(-1, TaskUserIdResolver.resolve(null));
    }

    @Test
    public void rejectsMissingUserIdField() {
        assertEquals(-1, TaskUserIdResolver.resolve(new MissingFieldTask()));
    }

    @Test
    public void rejectsWrongUserIdType() {
        assertEquals(-1, TaskUserIdResolver.resolve(new WrongTypeTask()));
    }

    @Test
    public void rejectsNegativeUserId() {
        assertEquals(-1, TaskUserIdResolver.resolve(new DirectTask(-1)));
    }

    private static final class DirectTask {
        private final int userId;

        DirectTask(int userId) {
            this.userId = userId;
        }
    }

    private static class BaseTask {
        private final int userId;

        BaseTask(int userId) {
            this.userId = userId;
        }
    }

    private static final class InheritedTask extends BaseTask {
        InheritedTask(int userId) {
            super(userId);
        }
    }

    private static final class MissingFieldTask {
    }

    private static final class WrongTypeTask {
        @SuppressWarnings("unused")
        private final String userId = "999";
    }
}
