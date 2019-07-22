package com.nisovin.shopkeepers.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.RegisteredListener;

import com.nisovin.shopkeepers.SKShopkeepersPlugin;
import com.nisovin.shopkeepers.Settings;

public class DebugListener implements Listener {

	public static void register() {
		// TODO might only log events whose classes got loaded yet (eg. with registered listeners).
		// TODO quite spammy. Filter/Reduce spamming events output.
		Log.info("Registering DebugListener to log all events!");
		DebugListener debugListener = new DebugListener();
		List<HandlerList> allHandlerLists = HandlerList.getHandlerLists();
		for (HandlerList handlerList : allHandlerLists) {
			handlerList.register(new RegisteredListener(debugListener, new EventExecutor() {
				@Override
				public void execute(Listener listener, Event event) throws EventException {
					debugListener.handleEvent(event);
				}
			}, EventPriority.LOWEST, SKShopkeepersPlugin.getInstance(), false));
		}
	}

	private static class EventData {
		boolean printedListeners = false;
	}

	private final Map<String, EventData> eventData = new HashMap<>();
	private String lastLoggedEvent = null;
	private int lastLoggedEventCounter = 0;

	private boolean logAllEvents = Settings.debugOptions.contains("log-all-events");
	private boolean printListeners = Settings.debugOptions.contains("print-listeners");

	DebugListener() {
	}

	public void handleEvent(Event event) {
		String eventName = event.getEventName();
		EventData data = eventData.get(eventName);
		if (data == null) {
			data = new EventData();
			eventData.put(eventName, data);
		}

		// event logging:
		if (logAllEvents) {
			// combine subsequent calls of the same event into single output that gets printed with the next event:
			if (eventName.equals(lastLoggedEvent)) {
				lastLoggedEventCounter++;
			} else {
				if (lastLoggedEventCounter > 0) {
					assert lastLoggedEvent != null;
					Log.info("[DebugListener] Event: " + lastLoggedEvent + " (" + lastLoggedEventCounter + "x" + ")");
				}

				lastLoggedEvent = eventName;
				lastLoggedEventCounter = 1;
			}
		}

		// print listeners, once:
		if (printListeners && !data.printedListeners) {
			data.printedListeners = true;
			Utils.printRegisteredListeners(event);
		}
	}
}
