# DataAwareDeclareReplayer

This is a tool for aligning data-aware Declare models with event logs

### Installation
You need to follow the instructions on the installation of ProM from [ProM tools installation page](http://www.promtools.org/doku.php?id=gettingstarted:installation).

After cloning or downloading this repository, import it into Eclipse.  The application uses [Ivy](https://ant.apache.org/ivy/) for dependency management. You may also need to install that.

You need start ProM from within the application in order to access our plugins.  To do that, right click on the ProM with UITopia (DataAwareDeclareReplayer).launch file and select Run As.

### How to use
An event log and a data-aware Declare model are needed as input.  
- An event log can be imported using the first tab.  
- Use the Simple Declare Designer (another ProM plugin) to create a data-aware Declare model.  This can be found in the middle tab and does not take any input.
- In the middle tab of ProM, select the Data Aware Declare Replayer plugin, add the above inputs and press start.
