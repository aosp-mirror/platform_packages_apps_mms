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
 *   1) REMOVE public boolean getModifierState(String keyIdentifierArg);
 *   2) REMOVE public void initMouseEventNS(String namespaceURIArg,
 *                               String typeArg,
 *                               boolean canBubbleArg,
 *                               boolean cancelableArg,
 *                               AbstractView viewArg,
 *                               int detailArg,
 *                               int screenXArg,
 *                               int screenYArg,
 *                               int clientXArg,
 *                               int clientYArg,
 *                               short buttonArg,
 *                               EventTarget relatedTargetArg,
 *                               String modifiersListArg);
 */

package org.w3c.dom.events;

import org.w3c.dom.views.AbstractView;

/**
 *  The <code>MouseEvent</code> interface provides specific contextual
 * information associated with Mouse events.
 * <p> In the case of nested elements mouse events are always targeted at the
 * most deeply nested element. Ancestors of the targeted element may use
 * bubbling to obtain notification of mouse events which occur within their
 * descendent elements.
 * <p> To create an instance of the <code>MouseEvent</code> interface, use the
 * <code>DocumentEvent.createEvent("MouseEvent")</code> method call.
 * <p ><b>Note:</b>  When initializing <code>MouseEvent</code> objects using
 * <code>initMouseEvent</code> or <code>initMouseEventNS</code>,
 * implementations should use the client coordinates <code>clientX</code>
 * and <code>clientY</code> for calculation of other coordinates (such as
 * target coordinates exposed by DOM Level 0 implementations).
 * <p>See also the <a href='http://www.w3.org/TR/2007/WD-DOM-Level-3-Events-20071207'>
   Document Object Model (DOM) Level 3 Events Specification
  </a>.
 * @since DOM Level 2
 */
public interface MouseEvent extends UIEvent {
    /**
     *  The horizontal coordinate at which the event occurred relative to the
     * origin of the screen coordinate system.
     */
    public int getScreenX();

    /**
     *  The vertical coordinate at which the event occurred relative to the
     * origin of the screen coordinate system.
     */
    public int getScreenY();

    /**
     *  The horizontal coordinate at which the event occurred relative to the
     * viewport associated with the event.
     */
    public int getClientX();

    /**
     *  The vertical coordinate at which the event occurred relative to the
     * viewport associated with the event.
     */
    public int getClientY();

    /**
     *  Refer to the <code>KeyboardEvent.ctrlKey</code> attribute.
     */
    public boolean getCtrlKey();

    /**
     *  Refer to the <code>KeyboardEvent.shiftKey</code> attribute.
     */
    public boolean getShiftKey();

    /**
     *  Refer to the <code>KeyboardEvent.altKey</code> attribute.
     */
    public boolean getAltKey();

    /**
     *  Refer to the <code>KeyboardEvent.metaKey</code> attribute.
     */
    public boolean getMetaKey();

    /**
     *  During mouse events caused by the depression or release of a mouse
     * button, <code>button</code> is used to indicate which mouse button
     * changed state. <code>0</code> indicates the normal button of the
     * mouse (in general on the left or the one button on Macintosh mice,
     * used to activate a button or select text). <code>2</code> indicates
     * the contextual property (in general on the right, used to display a
     * context menu) button of the mouse if present. <code>1</code>
     * indicates the extra (in general in the middle and often combined with
     * the mouse wheel) button. Some mice may provide or simulate more
     * buttons, and values higher than <code>2</code> can be used to
     * represent such buttons.
     */
    public short getButton();

    /**
     *  Used to identify a secondary <code>EventTarget</code> related to a UI
     * event, depending on the type of event.
     */
    public EventTarget getRelatedTarget();

    /**
     *  Initializes attributes of a <code>MouseEvent</code> object. This
     * method has the same behavior as <code>UIEvent.initUIEvent()</code>.
     * @param typeArg  Refer to the <code>UIEvent.initUIEvent()</code> method
     *   for a description of this parameter.
     * @param canBubbleArg  Refer to the <code>UIEvent.initUIEvent()</code>
     *   method for a description of this parameter.
     * @param cancelableArg  Refer to the <code>UIEvent.initUIEvent()</code>
     *   method for a description of this parameter.
     * @param viewArg  Refer to the <code>UIEvent.initUIEvent()</code> method
     *   for a description of this parameter.
     * @param detailArg  Refer to the <code>UIEvent.initUIEvent()</code>
     *   method for a description of this parameter.
     * @param screenXArg  Specifies <code>MouseEvent.screenX</code>.
     * @param screenYArg  Specifies <code>MouseEvent.screenY</code>.
     * @param clientXArg  Specifies <code>MouseEvent.clientX</code>.
     * @param clientYArg  Specifies <code>MouseEvent.clientY</code>.
     * @param ctrlKeyArg  Specifies <code>MouseEvent.ctrlKey</code>.
     * @param altKeyArg  Specifies <code>MouseEvent.altKey</code>.
     * @param shiftKeyArg  Specifies <code>MouseEvent.shiftKey</code>.
     * @param metaKeyArg  Specifies <code>MouseEvent.metaKey</code>.
     * @param buttonArg  Specifies <code>MouseEvent.button</code>.
     * @param relatedTargetArg  Specifies
     *   <code>MouseEvent.relatedTarget</code>. This value may be
     *   <code>null</code>.
     */
    public void initMouseEvent(String typeArg,
                               boolean canBubbleArg,
                               boolean cancelableArg,
                               AbstractView viewArg,
                               int detailArg,
                               int screenXArg,
                               int screenYArg,
                               int clientXArg,
                               int clientYArg,
                               boolean ctrlKeyArg,
                               boolean altKeyArg,
                               boolean shiftKeyArg,
                               boolean metaKeyArg,
                               short buttonArg,
                               EventTarget relatedTargetArg);

}
