package com.jayptucker.heroesevolved.network;
import com.jayptucker.heroesevolved.skills.SkillTreeData;
public final class ClientSkillTreeState {
    private static volatile SkillTreeStatePayload state = empty();
    private ClientSkillTreeState() {}
    private static SkillTreeStatePayload empty() { return new SkillTreeStatePayload("", false, 1, SkillTreeData.Build.empty()); }
    public static SkillTreeStatePayload get() { return state; }
    public static void update(SkillTreeStatePayload next) { state = next; }
    public static void clear() { state = empty(); }
}
