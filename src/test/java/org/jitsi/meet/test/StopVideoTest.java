/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jitsi.meet.test;

import org.jitsi.meet.test.base.*;
import org.jitsi.meet.test.util.*;

import org.openqa.selenium.*;
import org.testng.annotations.*;

/**
 * To stop the video on owner and participant side.
 * @author Damian Minkov
 */
public class StopVideoTest
    extends AbstractBaseTest
{
    /**
     * Default constructor.
     */
    public StopVideoTest()
    {}

    /**
     * Constructs StopVideoTest with already allocated participants.
     * @param participant1
     * @param participant2
     */
    public StopVideoTest(
        Participant participant1, Participant participant2)
    {
        this.participant1 = participant1;
        this.participant2 = participant2;
    }

    @Override
    public void setup()
    {
        super.setup();

        ensureTwoParticipants();
    }

    /**
     * Stops the video on the conference owner.
     */
    @Test
    public void stopVideoOnOwnerAndCheck()
    {
        MeetUIUtils.muteVideoAndCheck(
            participant1.getDriver(),
            participant2.getDriver());
    }

    /**
     * Starts the video on owner.
     */
    @Test(dependsOnMethods = { "stopVideoOnOwnerAndCheck" })
    public void startVideoOnOwnerAndCheck()
    {
        MeetUIUtils.clickOnToolbarButton(participant1.getDriver(),
            "toolbar_button_camera");

        // make sure we check at the remote videos on the second participant
        // side, otherwise if local is muted will fail
        TestUtils.waitForElementNotPresentOrNotDisplayedByXPath(
            participant2.getDriver(),
            "//span[starts-with(@id, 'participant_')]"
                + TestUtils.getXPathStringForClassName("//span", "videoMuted")
                + "/i[@class='icon-camera-disabled']", 10);

        TestUtils.waitForElementNotPresentOrNotDisplayedByXPath(
            participant1.getDriver(),
            "//span[@id='localVideoContainer']"
                + TestUtils.getXPathStringForClassName("//span", "videoMuted")
                + "/i[@class='icon-camera-disabled']", 10);
    }

    /**
     * Checks if muting/unmuting of the local video stream affects
     * large video of other participant.
     */
    @Test(dependsOnMethods = { "startVideoOnOwnerAndCheck" })
    public void stopAndStartVideoOnOwnerAndCheckStream()
    {
        WebDriver owner = participant1.getDriver();

        // mute owner
        stopVideoOnOwnerAndCheck();

        // now second participant should be on large video
        String secondParticipantVideoId = MeetUIUtils.getLargeVideoID(owner);

        // unmute owner
        startVideoOnOwnerAndCheck();

        // check if video stream from second participant is still on large video
        assertEquals("Large video stream id",
            secondParticipantVideoId,
            MeetUIUtils.getLargeVideoID(owner));
    }

    /**
     * Checks if muting/unmuting remote video triggers TRACK_ADDED or
     * TRACK_REMOVED events for the local participant.
     */
    @Test(dependsOnMethods = { "stopAndStartVideoOnOwnerAndCheckStream" })
    public void stopAndStartVideoOnOwnerAndCheckEvents()
    {
        WebDriver secondParticipant = participant2.getDriver();
        JavascriptExecutor executor = (JavascriptExecutor) secondParticipant;

        String listenForTrackRemoved = "APP.conference._room.addEventListener("
            + "JitsiMeetJS.events.conference.TRACK_REMOVED,"
            + "function () { APP._remoteRemoved = true; }"
            + ");";
        executor.executeScript(listenForTrackRemoved);

        String listenForTrackAdded = "APP.conference._room.addEventListener("
            + "JitsiMeetJS.events.conference.TRACK_ADDED,"
            + "function () { APP._remoteAdded = true; }"
            + ");";
        executor.executeScript(listenForTrackAdded);

        stopVideoOnOwnerAndCheck();
        startVideoOnOwnerAndCheck();

        TestUtils.waitMillis(1000);

        assertFalse("Remote stream was removed",
                    TestUtils.executeScriptAndReturnBoolean(
                                secondParticipant,
                                "return APP._remoteRemoved;"));
        assertFalse("Remote stream was added",
                    TestUtils.executeScriptAndReturnBoolean(
                                secondParticipant,
                                "return APP._remoteAdded;"));
    }

    /**
     * Stops the video on participant.
     */
    @Test(dependsOnMethods = { "stopAndStartVideoOnOwnerAndCheckEvents" })
    public void stopVideoOnParticipantAndCheck()
    {
        MeetUIUtils.muteVideoAndCheck(
            participant2.getDriver(),
            participant1.getDriver());
    }

    /**
     * Starts the video on participant.
     */
    @Test(dependsOnMethods = { "stopVideoOnParticipantAndCheck" })
    public void startVideoOnParticipantAndCheck()
    {
        MeetUIUtils.clickOnToolbarButton(
            participant2.getDriver(), "toolbar_button_camera");

        TestUtils.waitForElementNotPresentOrNotDisplayedByXPath(
            participant1.getDriver(),
            TestUtils.getXPathStringForClassName("//span", "videoMuted") +
            "/i[@class='icon-camera-disabled']", 5);
        
        TestUtils.waitForElementNotPresentOrNotDisplayedByXPath(
            participant2.getDriver(),
            TestUtils.getXPathStringForClassName("//span", "videoMuted") +
            "/i[@class='icon-camera-disabled']", 5);
    }

    /**
     * Closes the participant and leaves the owner alone in the room.
     * Stops video of the owner and then joins new participant and
     * checks the status of the stopped video icon.
     * At the end starts the video to clear the state.
     */
    @Test(dependsOnMethods = { "startVideoOnParticipantAndCheck" })
    public void stopOwnerVideoBeforeSecondParticipantJoins()
    {
        participant2.hangUp();

        // just in case wait
        TestUtils.waitMillis(1000);

        MeetUIUtils.clickOnToolbarButton(
            participant1.getDriver(),
            "toolbar_button_camera");

        TestUtils.waitMillis(500);

        ensureTwoParticipants();
        WebDriver secondParticipant = participant2.getDriver();

        MeetUtils.waitForParticipantToJoinMUC(secondParticipant, 10);
        MeetUtils.waitForIceConnected(secondParticipant);

        TestUtils.waitForElementByXPath(
            secondParticipant,
            TestUtils.getXPathStringForClassName("//span", "videoMuted")
            + "/i[@class='icon-camera-disabled']",
            5);

        // just debug messages
        /*{
            String ownerJid = (String) ((JavascriptExecutor)
                ConferenceFixture.getOwner())
                .executeScript("return APP.xmpp.myJid();");

            String streamByJid = "APP.RTC.remoteStreams['" + ownerJid + "']";
            System.err.println("Owner jid: " + ownerJid);

            Object streamExist = ((JavascriptExecutor)secondParticipant)
                .executeScript("return " + streamByJid + " != undefined;");
            System.err.println("Stream : " + streamExist);

            if (streamExist != null && streamExist.equals(Boolean.TRUE))
            {
                Object videoStreamExist
                    = ((JavascriptExecutor)secondParticipant).executeScript(
                        "return " + streamByJid + "['Video'] != undefined;");
                System.err.println("Stream exist : " + videoStreamExist);

                if (videoStreamExist != null && videoStreamExist
                    .equals(Boolean.TRUE))
                {
                    Object videoStreamMuted
                        = ((JavascriptExecutor) secondParticipant)
                            .executeScript(
                                "return " + streamByJid + "['Video'].muted;");
                    System.err.println("Stream muted : " + videoStreamMuted);
                }
            }
        }*/

        // now lets start video for owner
        startVideoOnOwnerAndCheck();

        // just in case wait
        TestUtils.waitMillis(1500);
    }

}
