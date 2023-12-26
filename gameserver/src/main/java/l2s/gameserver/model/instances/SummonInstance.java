package l2s.gameserver.model.instances;

import java.util.concurrent.Future;

import l2s.commons.lang.reference.HardReference;
import l2s.commons.threading.RunnableImpl;
import l2s.gameserver.ThreadPoolManager;
import l2s.gameserver.cache.Msg;
import l2s.gameserver.data.htm.HtmCache;
import l2s.gameserver.model.Creature;
import l2s.gameserver.model.Player;
import l2s.gameserver.model.Servitor;
import l2s.gameserver.model.Skill;
import l2s.gameserver.network.l2.components.SystemMsg;
import l2s.gameserver.network.l2.s2c.ExMagicAttackInfo;
import l2s.gameserver.network.l2.s2c.NpcHtmlMessagePacket;
import l2s.gameserver.network.l2.s2c.SetSummonRemainTimePacket;
import l2s.gameserver.network.l2.s2c.SystemMessage;
import l2s.gameserver.network.l2.s2c.SystemMessagePacket;
import l2s.gameserver.templates.npc.NpcTemplate;
import l2s.gameserver.templates.item.WeaponTemplate.WeaponType;

public class SummonInstance extends Servitor
{
	public final int CYCLE = 5000; // in millis
	private int _summonSkillId;
	private double _expPenalty = 0;
	private int _itemConsumeIdInTime;
	private int _itemConsumeCountInTime;
	private int _itemConsumeDelay;

	private Future<?> _disappearTask;

	private int _consumeCountdown;
	private int _lifetimeCountdown;
	private int _maxLifetime;

	public SummonInstance(int objectId, NpcTemplate template, Player owner, int lifetime, int consumeid, int consumecount, int consumedelay, Skill skill)
	{
		super(objectId, template, owner);
		setName(template.name);
		_lifetimeCountdown = _maxLifetime = lifetime;
		_itemConsumeIdInTime = consumeid;
		_itemConsumeCountInTime = consumecount;
		_consumeCountdown = _itemConsumeDelay = consumedelay;
		_summonSkillId = skill.getDisplayId();
		_disappearTask = ThreadPoolManager.getInstance().schedule(new Lifetime(), CYCLE);
	}

	@SuppressWarnings("unchecked")
	@Override
	public HardReference<SummonInstance> getRef()
	{
		return (HardReference<SummonInstance>) super.getRef();
	}

	@Override
	public final int getLevel()
	{
		return getTemplate() != null ? getTemplate().level : 0;
	}

	@Override
	public int getServitorType()
	{
		return 1;
	}

	@Override
	public int getCurrentFed()
	{
		return _lifetimeCountdown;
	}

	@Override
	public int getMaxFed()
	{
		return _maxLifetime;
	}

	public void setExpPenalty(double expPenalty)
	{
		_expPenalty = expPenalty;
	}

	@Override
	public double getExpPenalty()
	{
		return _expPenalty;
	}

	class Lifetime extends RunnableImpl
	{
		@Override
		public void runImpl() throws Exception
		{
			Player owner = getPlayer();
			if(owner == null)
			{
				_disappearTask = null;
				unSummon();
				return;
			}

			int usedtime = isInCombat() ? CYCLE : CYCLE / 4;
			_lifetimeCountdown -= usedtime;

			if(_lifetimeCountdown <= 0)
			{
				owner.sendPacket(Msg.SERVITOR_DISAPPEASR_BECAUSE_THE_SUMMONING_TIME_IS_OVER);
				_disappearTask = null;
				unSummon();
				return;
			}

			_consumeCountdown -= usedtime;
			if(_itemConsumeIdInTime > 0 && _itemConsumeCountInTime > 0 && _consumeCountdown <= 0)
				if(owner.getInventory().destroyItemByItemId(getItemConsumeIdInTime(), getItemConsumeCountInTime()))
				{
					_consumeCountdown = _itemConsumeDelay;
					owner.sendPacket(new SystemMessage(SystemMessage.A_SUMMONED_MONSTER_USES_S1).addItemName(getItemConsumeIdInTime()));
				}
				else
				{
					owner.sendPacket(Msg.SINCE_YOU_DO_NOT_HAVE_ENOUGH_ITEMS_TO_MAINTAIN_THE_SERVITORS_STAY_THE_SERVITOR_WILL_DISAPPEAR);
					unSummon();
				}

			owner.sendPacket(new SetSummonRemainTimePacket(SummonInstance.this));

			_disappearTask = ThreadPoolManager.getInstance().schedule(this, CYCLE);
		}
	}

	@Override
	protected void onDeath(Creature killer)
	{
		super.onDeath(killer);

		saveEffects();

		if(_disappearTask != null)
		{
			_disappearTask.cancel(false);
			_disappearTask = null;
		}
	}

	public int getItemConsumeIdInTime()
	{
		return _itemConsumeIdInTime;
	}

	public int getItemConsumeCountInTime()
	{
		return _itemConsumeCountInTime;
	}

	public int getItemConsumeDelay()
	{
		return _itemConsumeDelay;
	}

	protected synchronized void stopDisappear()
	{
		if(_disappearTask != null)
		{
			_disappearTask.cancel(false);
			_disappearTask = null;
		}
	}

	@Override
	public void unSummon()
	{
		stopDisappear();
		super.unSummon();
	}

	@Override
	public void displayGiveDamageMessage(Creature target, int damage, boolean crit, boolean miss, boolean shld, boolean magic)
	{
		Player owner = getPlayer();
		if(owner == null)
			return;

		if(miss)
		{
			/*TODOGOD:
			if(skill == null)*/
			if(!magic)
				owner.sendPacket(new SystemMessage(SystemMessage.C1S_ATTACK_WENT_ASTRAY).addName(this));
			else
				owner.sendPacket(new ExMagicAttackInfo(owner.getObjectId(), target.getObjectId(), ExMagicAttackInfo.EVADED)); // TODO: Нужно ли?
			return;
		}

		if(crit)
		{
			/*if(skill != null)
			{
				if(skill.isMagic())
					owner.sendPacket(SystemMsg.MAGIC_CRITICAL_HIT);
				owner.sendPacket(new ExMagicAttackInfo(owner.getObjectId(), target.getObjectId(), ExMagicAttackInfo.CRITICAL));
			}
			else*/
				owner.sendPacket(SystemMsg.SUMMONED_MONSTERS_CRITICAL_HIT);
		}

		if(!target.isInvul())
			owner.sendPacket(new SystemMessagePacket(SystemMsg.C1_HAS_DONE_S3_POINTS_OF_DAMAGE_TO_C2).addName(this).addName(target).addInteger(damage).addHpChange(target.getObjectId(), getObjectId(), -damage));
	}

	@Override
	public void displayReceiveDamageMessage(Creature attacker, int damage)
	{
		if(attacker != this)
			getPlayer().sendPacket(new SystemMessagePacket(SystemMsg.C1_HAS_RECEIVED_S3_DAMAGE_FROM_C2).addName(this).addName(attacker).addInteger(damage).addHpChange(getObjectId(), attacker.getObjectId(), -damage));
	}

	@Override
	public int getEffectIdentifier()
	{
		return _summonSkillId;
	}
	
	@Override
	public boolean isSummon()
	{
		return true;
	}

	@Override
	public void onAction(Player player, boolean shift)
	{
		super.onAction(player, shift);
		if(shift)
		{
			if(!player.getPlayerAccess().CanViewChar)
				return;

			String dialog;
			dialog = HtmCache.getInstance().getHtml("scripts/actions/admin.L2SummonInstance.onActionShift.htm", player);
			dialog = dialog.replaceFirst("%name%", String.valueOf(getName()));
			dialog = dialog.replaceFirst("%level%", String.valueOf(getLevel()));
			dialog = dialog.replaceFirst("%class%", String.valueOf(getClass().getSimpleName().replaceFirst("L2", "").replaceFirst("Instance", "")));
			dialog = dialog.replaceFirst("%xyz%", getLoc().x + " " + getLoc().y + " " + getLoc().z);
			dialog = dialog.replaceFirst("%heading%", String.valueOf(getLoc().h));

			dialog = dialog.replaceFirst("%owner%", String.valueOf(getPlayer().getName()));
			dialog = dialog.replaceFirst("%ownerId%", String.valueOf(getPlayer().getObjectId()));

			dialog = dialog.replaceFirst("%npcId%", String.valueOf(getNpcId()));
			dialog = dialog.replaceFirst("%expPenalty%", String.valueOf(getExpPenalty()));

			dialog = dialog.replaceFirst("%maxHp%", String.valueOf(getMaxHp()));
			dialog = dialog.replaceFirst("%maxMp%", String.valueOf(getMaxMp()));
			dialog = dialog.replaceFirst("%currHp%", String.valueOf((int) getCurrentHp()));
			dialog = dialog.replaceFirst("%currMp%", String.valueOf((int) getCurrentMp()));

			dialog = dialog.replaceFirst("%pDef%", String.valueOf(getPDef(null)));
			dialog = dialog.replaceFirst("%mDef%", String.valueOf(getMDef(null, null)));
			dialog = dialog.replaceFirst("%pAtk%", String.valueOf(getPAtk(null)));
			dialog = dialog.replaceFirst("%mAtk%", String.valueOf(getMAtk(null, null)));
			dialog = dialog.replaceFirst("%accuracy%", String.valueOf(getAccuracy()));
			dialog = dialog.replaceFirst("%evasionRate%", String.valueOf(getEvasionRate(null)));
			dialog = dialog.replaceFirst("%crt%", String.valueOf(getCriticalHit(null, null)));
			dialog = dialog.replaceFirst("%runSpeed%", String.valueOf(getRunSpeed()));
			dialog = dialog.replaceFirst("%walkSpeed%", String.valueOf(getWalkSpeed()));
			dialog = dialog.replaceFirst("%pAtkSpd%", String.valueOf(getPAtkSpd()));
			dialog = dialog.replaceFirst("%mAtkSpd%", String.valueOf(getMAtkSpd()));
			dialog = dialog.replaceFirst("%dist%", String.valueOf(getRealDistance(player)));

			dialog = dialog.replaceFirst("%STR%", String.valueOf(getSTR()));
			dialog = dialog.replaceFirst("%DEX%", String.valueOf(getDEX()));
			dialog = dialog.replaceFirst("%CON%", String.valueOf(getCON()));
			dialog = dialog.replaceFirst("%INT%", String.valueOf(getINT()));
			dialog = dialog.replaceFirst("%WIT%", String.valueOf(getWIT()));
			dialog = dialog.replaceFirst("%MEN%", String.valueOf(getMEN()));

			NpcHtmlMessagePacket msg = new NpcHtmlMessagePacket(5);
			msg.setHtml(dialog);
			player.sendPacket(msg);
		}
	}

	@Override
	public long getWearedMask()
	{
		return WeaponType.SWORD.mask(); // TODO: читать пассивки и смотреть тип оружия и брони там
	}
}