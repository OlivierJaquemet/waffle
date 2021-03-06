/**
 * Waffle (https://github.com/dblock/waffle)
 *
 * Copyright (c) 2010 - 2016 Application Security, Inc.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html.
 *
 * Contributors: Application Security, Inc.
 */
package waffle.mock;

import waffle.windows.auth.IWindowsImpersonationContext;

/**
 * The Class MockWindowsImpersonationContext.
 *
 * @author dblock[at]dblock[dot]org
 */
public class MockWindowsImpersonationContext implements IWindowsImpersonationContext {

    /*
     * (non-Javadoc)
     * @see waffle.windows.auth.IWindowsImpersonationContext#revertToSelf()
     */
    @Override
    public void revertToSelf() {
        // Do Nothing
    }
}
