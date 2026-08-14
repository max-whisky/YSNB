package com.YSNB.yuanshen.ui;

import static org.junit.Assert.assertEquals;

import com.YSNB.yuanshen.core.model.SavedAccount;
import java.util.List;
import org.junit.Test;

public final class CommunityNicknameRefreshPlannerTest {
    @Test
    public void prioritizesActiveAccountThenIncludesEveryMissingNickname() {
        List<SavedAccount> accounts = List.of(
                new SavedAccount("active", "已缓存昵称"),
                new SavedAccount("second", null),
                new SavedAccount("third", "其他昵称"),
                new SavedAccount("fourth", "")
        );

        assertEquals(
                List.of("active", "second", "fourth"),
                CommunityNicknameRefreshPlanner.build(accounts, "active")
        );
    }

    @Test
    public void withoutPriorityOnlyIncludesMissingNicknames() {
        List<SavedAccount> accounts = List.of(
                new SavedAccount("first", "已缓存昵称"),
                new SavedAccount("second", null),
                new SavedAccount("third", "")
        );

        assertEquals(
                List.of("second", "third"),
                CommunityNicknameRefreshPlanner.build(accounts, null)
        );
    }
}
