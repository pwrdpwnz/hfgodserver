package ai.residences.fortress.siege;

import l2s.commons.util.Rnd;
import l2s.gameserver.model.Creature;
import l2s.gameserver.model.entity.events.impl.FortressSiegeEvent;
import l2s.gameserver.model.entity.residence.Fortress;
import l2s.gameserver.model.instances.NpcInstance;
import l2s.gameserver.network.l2.components.NpcString;
import l2s.gameserver.network.l2.components.SystemMsg;
import l2s.gameserver.scripts.Functions;
import l2s.gameserver.data.xml.holder.SkillHolder;
import ai.residences.SiegeGuardFighter;
import npc.model.residences.SiegeGuardInstance;

/**
 * @author VISTALL
 * @date 16:43/17.04.2011
 */
public class General extends SiegeGuardFighter
{
	public General(NpcInstance actor)
	{
		super(actor);

		actor.addListener(FortressSiegeEvent.RESTORE_BARRACKS_LISTENER);
	}

	@Override
	public void onEvtAttacked(Creature attacker, int dam)
	{
		super.onEvtAttacked(attacker, dam);
		SiegeGuardInstance actor = getActor();

		if(Rnd.chance(1))
			Functions.npcSay(actor, NpcString.DO_YOU_NEED_MY_POWER_YOU_SEEM_TO_BE_STRUGGLING);
	}

	@Override
	public void onEvtSpawn()
	{
		super.onEvtSpawn();
		SiegeGuardInstance actor = getActor();

		FortressSiegeEvent siegeEvent = actor.getEvent(FortressSiegeEvent.class);
		if(siegeEvent == null)
			return;

		if(siegeEvent.getResidence().getFacilityLevel(Fortress.GUARD_BUFF) > 0)
			actor.doCast(SkillHolder.getInstance().getSkill(5432, siegeEvent.getResidence().getFacilityLevel(Fortress.GUARD_BUFF)), actor, false);

		siegeEvent.barrackAction(4, false);
	}

	@Override
	public void onEvtDead(Creature killer)
	{
		SiegeGuardInstance actor = getActor();
		FortressSiegeEvent siegeEvent = actor.getEvent(FortressSiegeEvent.class);
		if(siegeEvent == null)
			return;

		siegeEvent.barrackAction(4, true);

		siegeEvent.broadcastTo(SystemMsg.THE_BARRACKS_HAVE_BEEN_SEIZED, FortressSiegeEvent.ATTACKERS, FortressSiegeEvent.DEFENDERS);

		Functions.npcShout(actor, NpcString.I_FEEL_SO_MUCH_GRIEF_THAT_I_CANT_EVEN_TAKE_CARE_OF_MYSELF);

		super.onEvtDead(killer);

		siegeEvent.checkBarracks();
	}
}
