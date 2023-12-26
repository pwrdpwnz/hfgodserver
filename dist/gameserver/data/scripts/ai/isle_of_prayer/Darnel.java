package ai.isle_of_prayer;

import java.util.HashMap;
import java.util.Map;

import l2s.commons.threading.RunnableImpl;
import l2s.commons.util.Rnd;
import l2s.gameserver.ThreadPoolManager;
import l2s.gameserver.ai.DefaultAI;
import l2s.gameserver.data.xml.holder.NpcHolder;
import l2s.gameserver.idfactory.IdFactory;
import l2s.gameserver.model.Creature;
import l2s.gameserver.model.instances.NpcInstance;
import l2s.gameserver.model.instances.TrapInstance;
import l2s.gameserver.network.l2.s2c.MagicSkillUse;
import l2s.gameserver.model.Skill;
import l2s.gameserver.data.xml.holder.SkillHolder;
import l2s.gameserver.utils.Location;
import gnu.trove.map.hash.TIntObjectHashMap;
import instances.CrystalCaverns;

/**
 * @author Diamond
 */
public class Darnel extends DefaultAI
{
	private class TrapTask extends RunnableImpl
	{
		@Override
		public void runImpl()
		{
			NpcInstance actor = getActor();
			if(actor.isDead())
				return;

			// Спавним 10 ловушек
			TrapInstance trap;
			for(int i = 0; i < 10; i++)
			{
				trap = new TrapInstance(IdFactory.getInstance().getNextId(), NpcHolder.getInstance().getTemplate(13037), actor, trapSkills[Rnd.get(trapSkills.length)], new Location(Rnd.get(151896, 153608), Rnd.get(145032, 146808), -12584));
				trap.spawnMe();
			}
		}
	}

	final Skill[] trapSkills = new Skill[] {
			SkillHolder.getInstance().getSkill(5267, 1),
			SkillHolder.getInstance().getSkill(5268, 1),
			SkillHolder.getInstance().getSkill(5269, 1),
			SkillHolder.getInstance().getSkill(5270, 1) };

	final Skill Poison;
	final Skill Paralysis;

	public Darnel(NpcInstance actor)
	{
		super(actor);

		TIntObjectHashMap<Skill> skills = getActor().getTemplate().getSkills();

		Poison = skills.get(4182);
		Paralysis = skills.get(4189);
	}

	@Override
	protected boolean createNewTask()
	{
		clearTasks();
		Creature target;
		if((target = prepareTarget()) == null)
			return false;

		NpcInstance actor = getActor();
		if(actor.isDead())
			return false;

		int rnd_per = Rnd.get(100);

		if(rnd_per < 5)
		{
			actor.broadcastPacketToOthers(new MagicSkillUse(actor, actor, 5440, 1, 3000, 0));
			ThreadPoolManager.getInstance().schedule(new TrapTask(), 3000);
			return true;
		}

		double distance = actor.getDistance(target);

		if(!actor.isAMuted() && rnd_per < 75)
			return chooseTaskAndTargets(null, target, distance);

		Map<Skill, Integer> d_skill = new HashMap<Skill, Integer>();

		addDesiredSkill(d_skill, target, distance, Poison);
		addDesiredSkill(d_skill, target, distance, Paralysis);

		Skill r_skill = selectTopSkill(d_skill);

		return chooseTaskAndTargets(r_skill, target, distance);
	}

	@Override
	protected void onEvtAttacked(Creature attacker, int damage)
	{
		NpcInstance actor = getActor();
		CrystalCaverns refl = null;
		if(actor.getReflection() instanceof CrystalCaverns)
			refl = (CrystalCaverns) actor.getReflection();	
		if(refl != null)	
			refl.notifyDarnelAttacked();
		super.onEvtAttacked(attacker, damage);
	}

	@Override
	protected void onEvtDead(Creature killer)
	{
		NpcInstance actor = getActor();
		CrystalCaverns refl = null;
		if(actor.getReflection() instanceof CrystalCaverns)
			refl = (CrystalCaverns) actor.getReflection();	
		if(refl != null)	
			refl.notifyDarnelDead(getActor());
		super.onEvtDead(killer);
	}

	@Override
	protected boolean randomWalk()
	{
		return false;
	}
}