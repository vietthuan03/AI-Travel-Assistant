package com.example.travelassistant.core.data
import com.example.travelassistant.core.domain.*
import org.json.*
internal object PlanCodec {
 fun encode(brief: PlannerDraft,p: PlanPresentation): String {
 val b=JSONObject()
 b.put("destinationId",brief.destinationId)
 b.put("destinationQuery",brief.destinationQuery)
 b.put("startDate",brief.startDate)
 b.put("endDate",brief.endDate)
 b.put("adults",brief.adults)
 b.put("children",brief.children)
 b.put("childBands",brief.childBands)
 b.put("budget",brief.budget)
 b.put("currency",brief.currency)
 b.put("transport",brief.transport)
 b.put("originId",brief.originId)
 b.put("originQuery",brief.originQuery)
 b.put("purpose",brief.purpose)
 b.put("pace",brief.pace)
 b.put("interests",brief.interests)
 b.put("arrival",brief.arrival)
 b.put("departure",brief.departure)
 b.put("notes",brief.notes)
 b.put("outputLanguage",brief.outputLanguage)

 b.put("step",brief.step)
 val days=JSONArray();p.days.forEach { d -> val activities=JSONArray();d.activities.forEach { a -> activities.put(JSONObject().put("time",a.time).put("title",a.title).put("area",a.area).put("minutes",a.durationMinutes).put("note",a.note).put("sourceUrl",a.sourceUrl ?: JSONObject.NULL)) };days.put(JSONObject().put("date",d.date).put("title",d.title).put("activities",activities)) }
 val costs=JSONArray();p.costs.forEach { costs.put(JSONObject().put("category",it.category).put("amount",it.amount?.toPlainString() ?: JSONObject.NULL)) }
 return JSONObject().put("schema",1).put("brief",b).put("id",p.id).put("title",p.title).put("language",p.language).put("currency",p.currency).put("budget",p.requestedBudget.toPlainString()).put("group",p.group).put("version",p.version).put("days",days).put("costs",costs).put("assumption",p.assumption).put("transport",p.transportIncluded).put("sourceMode",p.sourceMode).toString()
 }
 fun decode(raw: String): Pair<PlannerDraft,PlanPresentation> {
 val o=JSONObject(raw);require(o.getInt("schema")==1);val b=o.getJSONObject("brief")
 val brief=PlannerDraft(destinationId=b.getString("destinationId"),destinationQuery=b.getString("destinationQuery"),startDate=b.getString("startDate"),endDate=b.getString("endDate"),adults=b.getString("adults"),children=b.getString("children"),childBands=b.getString("childBands"),budget=b.getString("budget"),currency=b.getString("currency"),transport=b.getString("transport"),originId=b.getString("originId"),originQuery=b.getString("originQuery"),purpose=b.getString("purpose"),pace=b.getString("pace"),interests=b.getString("interests"),arrival=b.getString("arrival"),departure=b.getString("departure"),notes=b.getString("notes"),outputLanguage=b.getString("outputLanguage"),step=b.getInt("step"))
 val days=o.getJSONArray("days");val costs=o.getJSONArray("costs")
 return brief to PlanPresentation(o.getString("id"),o.getString("title"),o.getString("language"),o.getString("currency"),o.getString("budget").toBigDecimal(),o.getInt("group"),o.getInt("version"),(0 until days.length()).map { i -> val d=days.getJSONObject(i);val a=d.getJSONArray("activities");PlanDay(d.getString("date"),d.getString("title"),(0 until a.length()).map { j -> val x=a.getJSONObject(j);PlanActivity(x.getString("time"),x.getString("title"),x.getString("area"),x.getInt("minutes"),x.getString("note"),if(x.isNull("sourceUrl")) null else x.getString("sourceUrl")) }) },(0 until costs.length()).map { i -> val c=costs.getJSONObject(i);PlanCost(c.getString("category"),if(c.isNull("amount")) null else c.getString("amount").toBigDecimal()) },o.getString("assumption"),o.getBoolean("transport"),o.optString("sourceMode","legacy"))
 }
}
