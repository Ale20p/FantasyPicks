# Core Premise: 
Pick players based on overall consensus of the ranking for each player.

# Features Required:
1) Player data:
	
	1) Fetch players' data: Grab the ranking data for each player from all sources the user wants to use when the user clicks on the fetch/update button.
	
	2) Data selection: The user needs to be able to select where they want the data to come from. For example, either from ESPN or Sleeper or both. Each source will have their own column for their respective ranking of the player. 

	3) Year selection option. Users can select which year the data they are viewing is for. 
		- If year is that of the current year/upcoming season then the application checks to find the most recent data.
		- If year is that of a season that has already pasted, then data is loaded from database since the season is already finished and there are no updates to that data. So if season has passed then that season's data should be in the database as a one and done. 

	4) League Type: Users can select what type of league they are drafting for. Which results in the corresponding data being scraped from the sources selected. League types:(Need to be non-superflex)
		- 1 PPR
		- 0.5 PPR
		- Non-PPR/Standard

2) Database: (There will be a `Get Data` button and a `Refresh Data` button)
	- When it's the first time running the application and there's no data, user can either click on `Get Data` or `Refresh Data` to get the same result of getting data since at that moment it's empty.

	- For the selected year, if the season has already finished, then application will get data from a source and store it permenantly. Since the year is finished, that data will never change. So if year is finished then the `Refresh Data` button does nothing.

	- Each time user clicks on `Get Data` it does 1 of 2 things, by checking if the user has updated data within the last 24 hours of data update with a `last_updated` parameter
		- If not, then automatically grabs most recent data available, so it tries to grab most recent available data and if it's successful then it replaces the data in the database, if not then user is notified that data retrival has failed and old data will continue to be used. 
		- If data has been updated within the last 24 hours, then data is not to be updated automatically, unless user species/manually clicks on `Refresh Data` to retrive new data and try to put it in database.
		- The `Refresh Data` button can only be clicked and activated every 10 minutes.

	- Data in databse will only consist of normalized data.
	
	-  If player in database doesn't have a value, there are unranked.

	- Lock actions when performed, so the user can't refresh data when they are getting data, and vice-versa.

	- The league types will act as different tables.

3) Showcasing data:
	
	- Rows: Each row will be a player and their respective data. 

	- Columns: 
		
		1) Overall ranking: The average ranking based on how many sources are choosen.
		2) Player name
		3) Position
		4) Team ticker
			
			Rest of columns: Rankings of the respective player from the respective sources. 

4) Drafting features:
	
	1) Player selected toggle: If a player is chosen, then the user clicks on the checkbox that should be to the left of the Rank column (the checkbox should be in the leftmost column) the row of the corresponding player turns gray. 
	
	
	