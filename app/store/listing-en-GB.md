# Play Store listing — en-GB

Mirrors `listing-fr-FR.md` section for section. The two say the same things about the product;
they are not translations of each other's sentences. When a feature changes, both files change
in the same commit — a listing that is accurate in one locale and stale in the other is worse
than one that is stale in both, because nobody notices.

Draft: nothing here has been pasted into the console yet.

## Short description (76 / 80)

```
Write your recipes, cook them step by step, share them with people you love.
```

## Full description (2136 / 4000)

```
Cook'n'Co is your family's and your friends' recipe book. You write down the dishes you
actually make, you find them again when it is time to cook, and the people who matter to you
do the same.

WRITING A RECIPE WITHOUT LOSING THE EVENING
The editor takes you through it in four passes: the basics, the ingredients, the steps, the
photo. Ingredients come from a shared catalogue, and the ones missing from it you add
yourself. Each step can carry its own timer and the ingredients it uses.

A COOK MODE BUILT FOR FULL HANDS
Start the recipe and follow it one step at a time, in large type. The timers ring even with
the screen off, and at every step you can see what you need. Cooking for six instead of four?
Change the servings and the quantities follow.

COOKBOOKS, ON YOUR OWN OR TOGETHER
Keep your recipes in cookbooks — the weeknight basics, the cakes, Christmas dinner. A cookbook
can stay private, be shared with a few people, or be open to everyone. Members of a shared one
add their own recipes to it: that is where the family book gets built.

A FEED, NOT AN ALGORITHM
The feed shows the recipes of the people you follow, in the order they arrive. Nothing else.
What you want to make again, you favourite: the list stays within reach.

FINDING THINGS, PROPERLY
Search for a recipe, an ingredient, a cookbook or a cook. Start from an ingredient to see what
people do with it, or sort the recipes however you like.

YOU DECIDE WHO READS YOU
Your account is public or on request, and you can approve followers one by one if you prefer.
Your own private notes on a recipe are visible to you alone.

THE APP FITS AROUND YOU
English or French, metric or imperial units, notifications as you want them. The language you
choose is also the one the emails you receive are written in.

ALSO
• A recipe opened on the web opens in the app: a shared link lands in the right place.
• Your recipes are on cooknco.eu too, with the same account.
• Connect your AI tools to your recipes, and take their access away whenever you want.

No advertising, no sponsored content, no infinite feed. Just your recipes and those of the
people you chose.
```

## Before pasting

- **Check the character counts**, which the console enforces and which this file claims
  rather than measures: 80 for the short description, 4000 for the full one.
- **The feature graphic is French-only.** `out/feature-graphic-1024x500.png` carries the
  French tagline, so an English listing wants its own — the Play Console takes one per
  locale. `feature-graphic.html` holds the copy to change.
- **The closing line is a commitment, not a flourish.** It is true today. If advertising,
  sponsored content or a ranked feed is ever added, this line comes out in the same change:
  a listing that contradicts the Data safety declaration is a policy problem rather than a
  wording one.
