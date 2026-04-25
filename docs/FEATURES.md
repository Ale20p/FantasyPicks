# Core Premise: 
Pick players based on overall consensus of the ranking for each player.

# Features Required:
1) Player data:
	
	1) Fetch players' data: Grab the ranking data for each player from all sources the user wants to use.
	
	2) Data selection: The user needs to be able to select where they want the data to come from. For example, either from ESPN or Sleeper or both. Each source will have their own column for their respective ranking of the player. 
	
	3) Database interaction: Each time user loads onto site, it grabs most recent data available, so it checks if version of data in database is different from current version, if so then old version is discarded and new version loaded.

2) Showcasing data:
	
	1) Players' data will be displayed in a table. Each player will an overall ranking which is the average from the number of different sources that the user selected and then each source will have it's own column with the respective ranking.

	2) Each player will have the ticker for the team they play for and their position, each of those will be a column. 
	