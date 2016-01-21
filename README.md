# seq

A Midi step sequencer that runs in the browser.

## Overview

![Screenshot of Seq](http://i.imgur.com/bs3DtHL.png)

Seq is a Midi step sequencer that runs in the browser. It uses the Web MIDI API for communicating with devices (Web MIDI is currently only supported in [Chrome](http://caniuse.com/#feat=midi)).

Seq creates a step sequencer for each output device that it finds. You can configure the output channel for each device, but that's it! 

Check out the [video at Vimeo](https://vimeo.com/146959755)

The plans for the future include:

- Multiple patterns per device
- De-activating MIDI devices 
- Saving the port and inactive devices between sessions
- Creating several parallel step sequencers for each device (with different ports)
- ~~Saving the sequences(!)~~
- ~~Showing the playhead~~ (currently unprecise)
- Support for Launchpad as input/output device (work in progress)
- Variable pattern length
- Shuffle
- Parameter sequencing (velocity, CC#, ...)

## Setup

To get going, run

    lein figwheel

and open your browser at [localhost:3449](http://localhost:3449/).
You should then see a step sequencer for each connected device. If you dis- or re-connect devices, the UI will reflect this.

## Tech stuff

Seq is implemented in ClojureScript and uses the React-wrapper, Reagent for the UI. 

For timing, it uses the techniques from Chris Wilson's blog post [A Tale of Two Clocks](http://www.html5rocks.com/en/tutorials/audio/scheduling/) with a more "functional" approach.

## License

Copyright © 2015 Betafunk

Distributed under the MIT License
