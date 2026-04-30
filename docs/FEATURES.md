# Core Premise: 
Pick players based on overall consensus of the ranking for each player.

# Features Required:
1) Player data:
	
	1) Fetch players' data: Grab the ranking data for each player from all sources the user wants to use when the user clicks on the fetch/update button.
	
	2) Data selection: The user needs to be able to select where they want the data to come from. For example, either from ESPN or Sleeper or both. Each source will have their own column for their respective ranking of the player. 
	
	3) Database interaction: Each time user loads onto site, it grabs most recent data available, so it checks if version of data in database is different from current version, if so then old version is discarded and new version loaded.

	4) Year selection option. Users can select which year the data they are viewing is for. 
		- If year is that of the current year/upcoming season then the application checks to find the most recent data.
		- If year is that of a season that has already pasted, then data is loaded from database since the season is already finished and there are no updates to that data. So if season has passed then that season's data should be in the database as a one and done. 

	5) League Type: Users can select what type of league they are drafting for. Which results in the corresponding data being scraped from the sources selected. 

2) Showcasing data:
	
	- Rows: Each row will be a player and their respective data. 

	- Columns: 
		
		1) Overall ranking: The average ranking based on how many sources are choosen.
		2) Player name
		3) Position
		4) Team ticker
			
			Rest of columns: Rankings of the respective player from the respective sources. 

3) Drafting features:
	
	1) Player selected toggle: If a player is chosen, then the user clicks on the checkbox that should be to the left of the Rank column (the checkbox should be in the leftmost column) the row of the corresponding player turns gray. 
	
	
	