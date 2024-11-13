/*
 * Copyright (c) 2001-2023 Mathew A. Nelson and Robocode contributors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://robocode.sourceforge.io/license/epl-v10.html
 */
package net.sf.robocode.repository.root.handlers;


import net.sf.robocode.core.Container;
import net.sf.robocode.repository.IRepository;
import net.sf.robocode.repository.root.IRepositoryRoot;

import java.io.File;
import java.util.List;
import java.util.Map;


/**
 * @author Pavel Savara (original)
 */
public abstract class RootHandler {
	public abstract void visitDirectory(File dir, boolean isDevel, Map<String, IRepositoryRoot> newRoots, IRepository repository, boolean force);

	public void open() {}

	public void close() {}

	public static void visitDirectories(File dir, boolean isDevel, Map<String, IRepositoryRoot> newRoots, IRepository repository, boolean force) {
		// walk thru all plugins
		final List<RootHandler> itemHandlerList = Container.getComponents(RootHandler.class);

		for (RootHandler handler : itemHandlerList) {
			handler.visitDirectory(dir, isDevel, newRoots, repository, force);
		}
	}

	public static void processHandlers(String action) {
		List<RootHandler> rootHandlers = Container.getComponents(RootHandler.class);
		for (RootHandler handler : rootHandlers) {
			if ("open".equals(action)) {
				handler.open();
			} else if ("close".equals(action)) {
				handler.close();
			}
		}
	}

	public static void openHandlers() {
		processHandlers("open");
	}

	public static void closeHandlers() {
		processHandlers("close");
	}

}
