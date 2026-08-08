import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const GROQ_API_KEY = Deno.env.get("GROQ_API_KEY");
const SUPABASE_URL = Deno.env.get("SUPABASE_URL");
const SUPABASE_ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY");

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (req.method !== "POST") {
    return new Response(JSON.stringify({ error: "Method not allowed" }), {
      status: 405,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  try {
    const { user_role, query } = await req.json();

    if (!user_role || !query) {
      return new Response(JSON.stringify({ error: "Missing required fields: user_role or query" }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    if (!GROQ_API_KEY) {
      return new Response(JSON.stringify({ error: "Groq API key is not configured on the server." }), {
        status: 500,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const supabase = createClient(SUPABASE_URL!, SUPABASE_ANON_KEY!);
    let systemPrompt = "";

    if (user_role === "student") {
      // 1. Query upcoming events context from Supabase
      const { data: events, error } = await supabase
        .from("events")
        .select("id, title, description, date_start, location, category")
        .gte("date_start", new Date().toISOString())
        .limit(10);

      if (error) {
        console.error("Supabase Query Error:", error);
      }

      systemPrompt = `You are a helpful student assistant for an event management application.
Your goal is to recommend relevant events based on the user's prompt.
Strictly use the provided event list to make recommendations.
Available Events: ${JSON.stringify(events || [])}`;

    } else if (user_role === "admin") {
      // 2. Format admin raw notes into an event summary
      systemPrompt = `You are an expert event management administrative AI assistant. 
Your task is to take raw event details, notes, or bullet points and format them into a publish-ready, engaging event summary:
- Event Overview
- Key Highlights & Takeaways
- Target Audience & Prerequisites (if applicable)`;

    } else {
      return new Response(JSON.stringify({ error: "Invalid user_role. Allowed roles: student, admin" }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    // Call Groq API via standard OpenAI-compatible endpoint
    const groqResponse = await fetch("https://api.groq.com/openai/v1/chat/completions", {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${GROQ_API_KEY}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        model: "llama-3.3-70b-versatile",
        messages: [
          { role: "system", content: systemPrompt },
          { role: "user", content: query }
        ],
        temperature: 0.6,
      }),
    });

    const aiData = await groqResponse.json();

    if (!groqResponse.ok) {
      return new Response(JSON.stringify({ error: aiData.error?.message || "Groq API request failed" }), {
        status: groqResponse.status,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const reply = aiData.choices?.[0]?.message?.content || "No recommendation or summary generated.";

    return new Response(JSON.stringify({ result: reply }), {
      headers: { ...corsHeaders, "Content-Type": "application/json" },
      status: 200,
    });

  } catch (error) {
    return new Response(JSON.stringify({ error: error.message }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});