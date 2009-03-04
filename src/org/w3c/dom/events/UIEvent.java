/*
 * Copyright (c) 2007 World Wide Web Consortium,
 *
 * (Massachusetts Institute of Technology, European Research Consortium for
 * Informatics and Mathematics, Keio University). All Rights Reserved. This
 * work is distributed under the W3C(r) Software License [1] in the hope that
 * it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * [1] http://www.w3.org/Consortium/Legal/2002/copyright-software-20021231
 *
 * Difference to the original copy of this file:
 *   1) public void initUIEventNS(String namespaceURIArg,
 *                            String typeArg,
 *                            boolean canBubbleArg,
 *                            boolean cancelableArg,
 *                            AbstractView viewArg,
 *                            int detailArg);
 */

package org.w3c.dom.events;

import org.w3c.dom.views.AbstractView;

/**
 *  The <code>UIEvent</code> interface provides specific contextual
 * information associated with User Interface events.
 * <p> To create an instance of the <code>UIEvent</code> interface, use the
 * <code>DocumentEvent.createEvent("UIEvent")</code> method call.
 * <p>See also the <a href='http://www.w3.org/TR/2007/WD-DOM-Level-3-Events-20071207'>
   Document Object Model (DOM) Level 3 Events Specification
  </a>.
 * @since DOM Level 2
 */
public interface UIEvent extends Event {
    /**
     *  The <code>view</code> attribute identifies the
     * <code>AbstractView</code> from which the event was generated.
     */
    public AbstractView getView();

    /**
     *  Specifies some detail information about the <code>Event</code>,
     * depending on the type of event.
     */
    public int getDetail();

    /**
     *  Initializes attributes of an <code>UIEvent</code> object. This method
     * has the same behavior as <code>Event.initEvent()</code>.
     * @param typeArg  Refer to the <code>Event.initEvent()</code> method for
     *   a description of this parameter.
     * @param canBubbleArg  Refer to the <code>Event.initEvent()</code>
     *   method for a description of this parameter.
     * @param cancelableArg  Refer to the <code>Event.initEvent()</code>
     *   method for a description of this parameter.
     * @param viewArg  Specifies <code>UIEvent.view</code>. This value may be
     *   <code>null</code>.
     * @param detailArg  Specifies <code>UIEvent.detail</code>.
     */
    public void initUIEvent(String typeArg,
                            boolean canBubbleArg,
                            boolean cancelableArg,
                            AbstractView viewArg,
                            int detailArg);

}
