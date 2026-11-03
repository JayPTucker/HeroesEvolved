package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import static com.jayptucker.heroesevolved.client.effects.AbilityEffectRenderer.*;
import static com.jayptucker.heroesevolved.client.effects.DetailedAbilityEffects.orbit;

/** Cold has its own silhouette: prismatic lances, rime, crystal fragments and wind-driven snow. */
final class CryoEffects {
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final int ICE = 0x9DDEFF, WHITE = 0xEDF9FF, BLUE = 0x5AA8E8;
    private CryoEffects() {}
    static boolean play(ClientLevel level, Kind kind, Vec3 origin, Vec3 target, float size, boolean detail) {
        switch (kind) {
            case CRYO_LANCE_FORGE, CRYO_LANCE_FLIGHT, CRYO_ARMOR, CRYO_SNAP,
                    CRYO_REBOUND, CRYO_COCOON, CRYO_CONSTELLATION -> {
                CryoSkillVisuals.play(level,kind,origin,target,size);
                if(kind==Kind.CRYO_LANCE_FORGE&&detail) {
                    Vec3 axis=target.subtract(origin).normalize();
                    mote(level,origin.add(axis.scale(.3)),axis.scale(-.025),.045F,8,WHITE);
                }
                if(kind==Kind.CRYO_SNAP)ring(level,origin.add(0,.04,0),UP,Math.max(.1F,size-.3F),size+.15F,8,0,WHITE,.4F);
                if(kind==Kind.CRYO_REBOUND)CryoMeshVisuals.shatter(level,origin,1,detail?8:3);
                if(kind==Kind.CRYO_CONSTELLATION&&detail)branch(level,origin.add(0,.45,0),target.add(0,.45,0),.35F+size*.3F,false);
            }
            case CRYO_LOOM -> {
                CryoSkillVisuals.play(level,kind,origin,target,size);
                CryoMeshVisuals.ribbon(level,origin,target,Math.clamp(size,.5F,3));
                CryoMeshVisuals.sculptCell(level,target.add(0,-.05,0));
                branch(level,origin.add(0,.03,0),target.add(0,.03,0),.45F,detail);
            }
            case CRYO_RIME_STREAM -> rime(level, origin, target, size, detail);
            case CRYO_SCULPT -> sculpt(level, origin, target, size, detail);
            case CRYO_RIDE -> ride(level, origin, target.subtract(origin), detail);
            case CRYO_RIBBON -> {
                CryoMeshVisuals.ribbon(level, origin, target, size);
                branch(level, origin.add(0,.035,0), target.add(0,.035,0), .3F, detail);
            }
            case CRYO_PRISON -> prison(level, origin, target, size, detail);
            case CRYO_FRACTURE -> fracture(level, origin, target, size, detail);
            case CRYO_WHITEOUT -> whiteout(level, origin, size, detail);
            case CRYO_BRANCH -> branch(level, origin, target, size, detail);
            case CRYO_BOLT -> {
                Vec3 direction = target.subtract(origin).normalize();
                streak(level, origin, target, 0.055F, 12, WHITE);
                Vec3 across = direction.cross(Math.abs(direction.y) > .9 ? new Vec3(1, 0, 0) : UP).normalize().scale(0.08);
                if (detail) {
                    streak(level, origin.add(across), target, 0.035F, 15, ICE);
                    streak(level, origin.subtract(across), target, 0.035F, 15, BLUE);
                }
                int rings = Math.clamp((int) origin.distanceTo(target) / 3, 1, 14);
                for (int i = 0; i < rings; i++) {
                    Vec3 point = origin.lerp(target, (i + 1.0) / (rings + 1));
                    ring(level, point, direction, 0.16F, 0.4F, 10, i / 3, ICE, 0.6F);
                    if (detail) mote(level, point, new Vec3(0, -0.04, 0), 0.07F, 20, WHITE);
                }
                crystals(level, target, 0.5F, detail ? 10 : 4, 14);
            }
            case CRYO_CHILL -> {
                float height = (float) Math.clamp(target.y - origin.y, 0.5, 6);
                ring(level, origin.add(0, 0.08, 0), UP, size * 0.5F, size * 0.8F, 18, 0, ICE, 0.35F);
                for (int i = 0; i < (detail ? 8 : 3); i++) {
                    double a = i * 2.4 + level.getGameTime() * 0.1;
                    Vec3 p = origin.add(Math.cos(a) * size * 0.6, height * i / 8, Math.sin(a) * size * 0.6);
                    mote(level, p, new Vec3(0.01 * Math.cos(a), -0.025, 0.01 * Math.sin(a)), 0.045F, 18, WHITE);
                }
            }
            case CRYO_FREEZE -> {
                float height = (float) Math.clamp(target.y - origin.y, 0.5, 8);
                for (int i = 0; i < (detail ? 5 : 2); i++)
                    ring(level, origin.add(0, height * i / (detail ? 4 : 1), 0), UP,
                            size * 1.7F, size * 0.65F, 18, i * 2, i % 2 == 0 ? ICE : WHITE, 0.7F);
                crystals(level, origin.add(0, height * 0.4, 0), size, detail ? 16 : 6, 16);
            }
            case CRYO_SHATTER -> {
                Vec3 center = origin.lerp(target, 0.5);
                CryoMeshVisuals.shatter(level, center, Math.max(1, size), detail ? 18 : 7);
                crystals(level, center, Math.max(1.5F, size * 2), detail ? 28 : 10, 22);
                burst(level, center, UP, WHITE, detail ? 24 : 8, 0.23F);
                ring(level, center, UP, 0.2F, size * 2.2F, 12, 0, ICE, 0.7F);
            }
            case CRYO_ICE_GROWTH -> {
                CryoMeshVisuals.grow(level, origin, target.add(0,.15,0), Math.max(.15F, size*.38F));
                float height = (float) Math.max(1, target.y - origin.y);
                ring(level, origin.add(0, 0.05, 0), UP, 0.2F, 1.1F, 16, 0, ICE, 0.6F);
                for (int i = 0; i < (detail ? 6 : 3); i++) {
                    double a = i * Math.PI / 3;
                    Vec3 base = origin.add(Math.cos(a) * 0.5, 0, Math.sin(a) * 0.5);
                    streak(level, base, base.add(-Math.cos(a) * 0.2, height + 0.3, -Math.sin(a) * 0.2),
                            0.055F, 16, i % 2 == 0 ? WHITE : BLUE);
                }
            }
            case CRYO_BLIZZARD -> {
                float phase = level.getGameTime() * 0.05F;
                for (int layer = 0; layer < (detail ? 4 : 2); layer++)
                    orbit(level, origin.add(0, 0.5 + layer * 1.4, 0), new Vec3(0.05, 1, 0.1),
                            size * (0.45F + layer * 0.15F), 20, ICE, 0.08F, target.subtract(origin));
                for (int i = 0; i < (detail ? 75 : 18); i++) {
                    double a = i * 2.39996 + phase, radius = Math.sqrt((i + 0.5) / (detail ? 75 : 18)) * size;
                    Vec3 p = origin.add(Math.cos(a) * radius, level.random.nextDouble() * 7, Math.sin(a) * radius);
                    mote(level, p, new Vec3(-Math.sin(a) * 0.20, -0.09, Math.cos(a) * 0.20),
                            0.045F + level.random.nextFloat() * 0.04F, 24, i % 4 == 0 ? BLUE : WHITE);
                }
            }
            case CRYO_AWAKEN_CHARGE -> {
                ring(level, origin.add(0, 0.08, 0), UP, 1 + size * 3, 0.5F, 14, 0, ICE, 0.6F);
                helix(level, origin, 0.8F + size, 2.3F, WHITE, detail ? 18 : 6, 16);
                crystals(level, origin.add(0, 0.8, 0), 0.4F + size * 0.3F, detail ? 8 : 3, 12);
            }
            case CRYO_AWAKEN_RELEASE -> {
                for (int i = 0; i < (detail ? 3 : 1); i++)
                    ring(level, origin.add(0, 0.12 + i * 0.35, 0), UP, 0.4F, size + i,
                            30, i * 3, i == 1 ? WHITE : ICE, 0.7F);
                crystals(level, origin.add(0, 1, 0), size, detail ? 36 : 12, 30);
                helix(level, origin, 3, 6, WHITE, detail ? 48 : 12, 35);
            }
            default -> { return false; }
        }
        return true;
    }
    /** A cold stream has a narrow moving core and falling ice, with rime branching at contact. */
    private static void rime(ClientLevel level, Vec3 from, Vec3 to, float size, boolean detail) {
        Vec3 delta=to.subtract(from),axis=delta.normalize();
        Vec3 right=side(axis), up=axis.cross(right).normalize();
        int points=detail?15:6;
        Vec3 previous=from;
        for(int i=1;i<=points;i++) {
            double t=i/(double)points, phase=level.getGameTime()*.3+t*10;
            Vec3 p=from.add(delta.scale(t)).add(right.scale(Math.sin(phase)*.06*t))
                    .add(up.scale(Math.cos(phase)*.06*t));
            streak(level,previous,p,.045F,7,i%3==0?WHITE:ICE);
            if(i%2==0)level.addParticle(ParticleTypes.SNOWFLAKE,p.x,p.y,p.z,axis.x*.08,-.025,axis.z*.08);
            previous=p;
        }
        mote(level,from,axis.scale(.12),.10F,6,WHITE);
        Vec3 base=to.add(0,.02,0);
        for(int i=0;i<(detail?4:2);i++) {
            double a=level.getGameTime()*.1+i*Math.PI/2;
            branch(level,base,base.add(Math.cos(a)*.8,0,Math.sin(a)*.8),.25F,false);
        }
        if(detail)CryoMeshVisuals.grow(level,to,to.add(axis.scale(.25)).add(0,.12,0),.07F);
    }
    private static void sculpt(ClientLevel level,Vec3 base,Vec3 top,float progress,boolean detail) {
        CryoMeshVisuals.sculptCell(level, base);
        float growth=Math.clamp(progress,0,1),radius=.28F+growth*.24F;
        Vec3 axis=top.subtract(base);if(axis.lengthSqr()<.1)axis=UP;
        CryoMeshVisuals.grow(level,base,base.add(axis),radius);
        if(detail)for(int i=0;i<3;i++) {
            double a=i*Math.PI*2/3;
            Vec3 foot=base.add(Math.cos(a)*.38,.02,Math.sin(a)*.38);
            CryoMeshVisuals.grow(level,foot,foot.add(axis.scale(.65)).add(0,.15,0),radius*.6F);
        }
        branch(level,base.add(-.55,.03,-.4),base,.4F,detail);
        branch(level,base.add(.5,.03,.45),base,.4F,detail);
        ring(level,base.add(0,.025,0),UP,.1F,.85F,12,0,ICE,.4F);
    }
    private static void ride(ClientLevel level,Vec3 feet,Vec3 velocity,boolean detail) {
        Vec3 backward=velocity.lengthSqr()>.01?velocity.normalize().reverse():new Vec3(0,0,-1);
        Vec3 lateral=side(backward);
        for(int sign:new int[]{-1,1}) {
            Vec3 skate=feet.add(lateral.scale(sign*.2));
            streak(level,skate,skate.add(backward.scale(.8)),.055F,8,WHITE);
            CryoMeshVisuals.grow(level,skate.add(backward.scale(.25)).add(0,-.05,0),
                    skate.add(backward.scale(.5)).add(0,.18,0),.075F);
            for(int i=0;i<(detail?4:2);i++) {
                Vec3 p=skate.add(backward.scale(level.random.nextDouble()));
                level.addParticle(ParticleTypes.SNOWFLAKE,p.x,p.y+.12,p.z,
                        backward.x*.08,.025,backward.z*.08);
            }
        }
    }
    private static void prison(ClientLevel level,Vec3 feet,Vec3 head,float progress,boolean detail) {
        float amount=Math.clamp(progress,0,1),height=(float)Math.clamp(head.y-feet.y,.6,6);
        int ribs=detail?6:3;
        for(int i=0;i<ribs;i++) {
            double a=i*Math.PI*2/ribs;
            Vec3 base=feet.add(Math.cos(a)*.42,.02,Math.sin(a)*.42);
            CryoMeshVisuals.grow(level,base,base.add(-Math.cos(a)*.12,height*(.25+amount*.75),-Math.sin(a)*.12),.13F);
        }
        ring(level,feet.add(0,.05,0),UP,.7F,.38F,14,0,WHITE,.65F);
        if(detail)for(int i=0;i<8;i++) {
            double a=i*2.39996;
            Vec3 p=feet.add(Math.cos(a)*.6,height*(i/8.0),Math.sin(a)*.6);
            mote(level,p,new Vec3(0,.04,0),.065F,16,ICE);
        }
    }
    private static void fracture(ClientLevel level,Vec3 center,Vec3 target,float radius,boolean detail) {
        CryoMeshVisuals.shatter(level,center.add(0,.8,0),radius,detail?24:8);
        ring(level,center.add(0,.12,0),UP,.1F,radius,14,0,WHITE,.7F);
        burst(level,center.add(0,.6,0),UP,ICE,detail?18:7,.18F);
        int branches=detail?8:4;
        for(int i=0;i<branches;i++) {
            double a=i*Math.PI*2/branches;
            branch(level,center.add(0,.04,0),center.add(Math.cos(a)*radius,.04,Math.sin(a)*radius),.6F,detail);
        }
    }
    /** Lattice spokes and layered snow have depth; the far edge is still readable inside the storm. */
    private static void whiteout(ClientLevel level,Vec3 center,float radius,boolean detail) {
        ring(level,center.add(0,.08,0),UP,radius*.92F,radius,24,0,ICE,.55F);
        int spokes=detail?10:5;
        for(int i=0;i<spokes;i++) {
            double a=i*Math.PI*2/spokes+level.getGameTime()*.008;
            Vec3 edge=center.add(Math.cos(a)*radius,.06,Math.sin(a)*radius);
            branch(level,center.add(0,.06,0),edge,.4F,detail);
            if(i%2==0)CryoMeshVisuals.grow(level,edge,edge.add(0,.8+level.random.nextDouble()*.8,0),.22F);
        }
        int flakes=detail?36:12;
        for(int i=0;i<flakes;i++) {
            double a=i*2.39996+level.getGameTime()*.04,r=Math.sqrt((i+.5)/flakes)*radius;
            Vec3 p=center.add(Math.cos(a)*r,.3+level.random.nextDouble()*4.5,Math.sin(a)*r);
            level.addParticle(ParticleTypes.SNOWFLAKE,p.x,p.y,p.z,-Math.sin(a)*.11,-.045,Math.cos(a)*.11);
        }
    }
    /** Small delayed forks create dendritic ice growth instead of a single straight laser. */
    private static void branch(ClientLevel level,Vec3 from,Vec3 to,float size,boolean detail) {
        Vec3 delta=to.subtract(from),axis=delta.normalize(),lateral=side(axis);
        int pieces=detail?8:4;
        Vec3 previous=from;
        for(int i=1;i<=pieces;i++) {
            double t=i/(double)pieces;
            Vec3 p=from.add(delta.scale(t)).add(lateral.scale(Math.sin(t*11+level.getGameTime()*.07)*.045));
            add(new PowerParticle(level,PowerParticle.Shape.STREAK,previous,p,.025F+size*.018F,.008F,
                    18,i/2,i%2==0?WHITE:ICE,.7F));
            if(i%2==0) {
                Vec3 fork=p.subtract(axis.scale(.16)).add(lateral.scale((i%4==0?1:-1)*(.18+size*.25)));
                add(new PowerParticle(level,PowerParticle.Shape.STREAK,p,fork,.018F,.004F,16,2+i/2,WHITE,.65F));
            }
            previous=p;
        }
    }
    private static Vec3 side(Vec3 axis) {
        return axis.cross(Math.abs(axis.y)>.9?new Vec3(1,0,0):UP).normalize();
    }
    private static void crystals(ClientLevel level, Vec3 center, float radius, int count, int ticks) {
        for (int i = 0; i < count; i++) {
            double a = i * 2.39996;
            Vec3 radial = new Vec3(Math.cos(a), Math.sin(i * 1.7) * 0.7, Math.sin(a)).normalize();
            Vec3 end = center.add(radial.scale(radius)), base = end.subtract(radial.scale(0.25));
            add(new PowerParticle(level, PowerParticle.Shape.CRYSTAL, end, radial,
                    .10F, .025F, ticks, i % 3, i % 2 == 0 ? ICE : WHITE, .85F)
                    .moving(radial.scale(.07).add(0, -.015, 0)));
            mote(level, end, radial.scale(0.08), 0.055F, ticks, WHITE);
        }
    }
}
